package local.muse.patches.sharemenu.hooks

import local.muse.patches.MuseConstants.COMPATIBILITY_X
import local.muse.patches.MuseConstants.NATIVE_DESCRIPTOR
import local.muse.patches.entity.decoder.TWEET_ITEM_CLASS_NAME
import local.muse.patches.entity.decoder.decoderEntity
import local.muse.patches.sharemenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import local.muse.utils.indexOfFirstInstruction
import local.muse.utils.indexOfFirstInstructionOrThrow
import local.muse.utils.registersUsed
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_NAME = "${NATIVE_DESCRIPTOR}/ButtonPressHandler;->"

@Suppress("unused")
val shareMenuButtonOnClickHook =
    bytecodePatch(
        description = "Hook required to configure share menu onclick action",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(decoderEntity)

        execute {
            ShareMenuButtonFuncCallFingerprint.apply {
                val isStaticMethod = AccessFlags.STATIC.isSet(method.accessFlags)

                method.apply {
                    var tweetObjectParameter = parameters.indexOfFirst { it.type == TWEET_ITEM_CLASS_NAME }
                    // If it is not a static function, then we need to increase the parameter count by one.
                    if (!isStaticMethod) {
                        tweetObjectParameter += 1
                    }

                    val contextIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)
                    val contextRegister = getInstruction(contextIfEqzIndex).registersUsed[0]

                    val buttonEnumPressCheckIndex = indexOfFirstInstruction(contextIfEqzIndex, Opcode.IF_NE)
                    val buttonPressedRegister = getInstruction(buttonEnumPressCheckIndex).registersUsed[0]

                    // Another bundle (e.g. piko itself) may already have hooked this
                    // dispatch method with its own isButtonPressed block in the same
                    // patch session. Inserting a second block at the same anchor
                    // reuses the button enum register as the scratch register and
                    // corrupts the method (the app then fails verification at
                    // startup). Chain this dispatch after the existing block
                    // instead: run only when the earlier handler did not match.
                    val existingHookIndex =
                        instructions.indexOfFirst { ins ->
                            (ins as? Instruction35c)?.reference?.let { ref ->
                                (ref as? MethodReference)?.name == "isButtonPressed"
                            } == true
                        }

                    val insertIndex: Int
                    if (existingHookIndex >= 0) {
                        val theirReturnIndex =
                            (existingHookIndex until minOf(existingHookIndex + 10, instructions.size))
                                .first { instructions[it].opcode == Opcode.RETURN_VOID }
                        insertIndex = theirReturnIndex + 1
                    } else {
                        insertIndex = contextIfEqzIndex + 1
                    }

                    val nextInstruction = getInstruction(insertIndex)
                    val freeRegister = nextInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        insertIndex,
                        """
                        move-object/from16 v$freeRegister, v$buttonPressedRegister
                        invoke-static {v$buttonPressedRegister}, ${EXTENSION_CLASS_NAME}isButtonPressed(Ljava/lang/Object;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :muse
                        move-object/from16 v$freeRegister, p$tweetObjectParameter
                        invoke-static {v$contextRegister,v$buttonPressedRegister,v$freeRegister}, ${EXTENSION_CLASS_NAME}buttonPressAction(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                        """.trimIndent(),
                        ExternalLabel("muse", nextInstruction),
                    )
                }
            }
        }
    }
