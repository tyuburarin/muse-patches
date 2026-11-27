package local.muse.patches.video

import local.muse.patches.MuseConstants.COMPATIBILITY_X
import local.muse.utils.getReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PLAYER_CLASS = "Lcom/twitter/media/av/player/u;"
private const val AUDIO_CLASS = "Lcom/twitter/media/av/player/audio/a;"
private const val AUDIO_LISTENER = "Lcom/twitter/media/av/player/audio/a\$a;"

// R.string.media_error_audio_focus_rejected —
// "This video could not be played because another Application is using audio."
// Referenced exactly once in the whole app: inside u.N, in the error
// branch of the audio-focus gate below.
private const val AUDIO_FOCUS_ERROR_RES = 0x7f150ff9

/**
 * The play command of the AV player core,
 * Lcom/twitter/media/av/player/u;->N:(Z)V.
 *
 * Identified by the class/name/signature plus two in-method anchors:
 * the call into the player's audio-focus service and the
 * media_error_audio_focus_rejected resource literal, which exists
 * nowhere else in the app.
 */
internal object AvPlayerPlayFingerprint : Fingerprint(
    definingClass = PLAYER_CLASS,
    name = "N",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            definingClass = AUDIO_CLASS,
            name = "a",
            parameters = listOf(AUDIO_LISTENER),
            returnType = "Z",
        ),
        literal(AUDIO_FOCUS_ERROR_RES),
    ),
)

/**
 * "Allow Video Playback During Calls" — 通話中の動画再生を許可.
 *
 * Root cause (X 12.19.1, investigated 2026-11-20): the fullscreen
 * player's explicit play command, u.N(Z)V, runs an audio-focus gate
 * before it may start playback:
 *
 *   if (J — focus-required flag from the playback config — and type != GIF) {
 *       if (!audioFocusService.a(this))   // requestAudioFocus(...)
 *           -> post error event media_error_audio_focus_rejected
 *              and return WITHOUT starting playback
 *   }
 *
 * During a LINE voice call the focus request is not granted, so the
 * fullscreen player aborts here even though nothing in the player
 * itself failed. The timeline player does not hit this: it starts
 * muted/autoplay and unmutes through u.I(ZZ)V, which reports the same
 * focus rejection only as an event and keeps playing — and the same
 * method N already exempts GIFs from the gate entirely, so playing
 * without holding audio focus is an anticipated mode of this player.
 *
 * The patch changes exactly one instruction: the IF_EQZ that diverts
 * a rejected focus request into the error branch becomes a NOP, so
 * execution always falls through to the stock "granted" path (the
 * goto to the normal playback code right after it).
 *
 * What this deliberately does NOT do:
 *  - requestAudioFocus is still called, with its real result. Nothing
 *    is faked to GRANTED, here or anywhere else.
 *  - The other app's (LINE's) focus is never taken, abandoned or
 *    otherwise touched; a rejected request leaves X holding nothing.
 *  - The timeline path (u.I) and every other media code path are
 *    untouched; only this one branch in this one method changes.
 *  - The error branch itself stays in place, just unreachable from
 *    this gate.
 */
@Suppress("unused")
val allowVideoPlaybackDuringCallsPatch =
    bytecodePatch(
        name = "Allow Video Playback During Calls",
        description = "LINE通話などで音声を使用しているときも、Xの全画面動画を再生できるようにします。",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        execute {
            val fingerprint = AvPlayerPlayFingerprint
            if (fingerprint.classDef.type != PLAYER_CLASS) {
                throw PatchException(
                    "AV player play fingerprint matched an unexpected class: ${fingerprint.classDef.type}",
                )
            }
            val method = fingerprint.method
            if (method.name != "N") {
                throw PatchException(
                    "AV player play fingerprint matched an unexpected method: ${method.name}",
                )
            }
            val code = method.instructions.toList()

            // The method must really be the one carrying the focus-error
            // literal (the fingerprint filter already implies this; the
            // explicit check keeps the patch honest if matching loosens).
            val hasErrorLiteral = code.any {
                (it as? NarrowLiteralInstruction)?.narrowLiteral == AUDIO_FOCUS_ERROR_RES ||
                    (it as? WideLiteralInstruction)?.wideLiteral == AUDIO_FOCUS_ERROR_RES.toLong()
            }
            if (!hasErrorLiteral) {
                throw PatchException("Audio-focus error literal not found in u.N")
            }

            // Locate the gate: the single audio-focus request call.
            val callIndexes = code.withIndex().filter { (_, instruction) ->
                instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    instruction.getReference<MethodReference>()?.let {
                        it.definingClass == AUDIO_CLASS && it.name == "a" &&
                            it.returnType == "Z" &&
                            it.parameterTypes.map(CharSequence::toString) ==
                            listOf(AUDIO_LISTENER)
                    } == true
            }.map { it.index }
            if (callIndexes.size != 1) {
                throw PatchException(
                    "Expected exactly one audio-focus request in u.N, found ${callIndexes.size}",
                )
            }
            val callIndex = callIndexes.single()

            // Expected shape (verified against 12.19.1):
            //   invoke-interface audio/a.a(listener) Z
            //   move-result v0
            //   if-eqz v0, <error branch>     <- replaced with nop
            //   goto <normal playback path>
            if (code.getOrNull(callIndex + 1)?.opcode != Opcode.MOVE_RESULT ||
                code.getOrNull(callIndex + 2)?.opcode != Opcode.IF_EQZ ||
                code.getOrNull(callIndex + 3)?.opcode != Opcode.GOTO
            ) {
                throw PatchException("Unexpected audio-focus gate shape in u.N")
            }

            method.replaceInstruction(callIndex + 2, "nop")
        }
    }
