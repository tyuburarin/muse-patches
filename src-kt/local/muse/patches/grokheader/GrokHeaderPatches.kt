package local.muse.patches.grokheader

import local.muse.patches.MuseConstants.COMPATIBILITY_X
import local.muse.patches.entity.entityGenerator
import local.muse.utils.extendWithBundled
import local.muse.utils.indexOfFirstInstructionReversedOrThrow
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode

private const val HANDLER_CLASS =
    "Llocal/muse/extension/twitter/patches/nativeFeatures/HeaderGrokButtons;"
private const val BIND_CLASS = "Lcom/twitter/tweetview/core/ui/grok/e;"
private const val DELEGATE_CLASS = "Lcom/twitter/tweetview/core/ui/grok/c;"
private const val STATE_CLASS = "Lcom/twitter/tweetview/core/z;"
private const val TWEET_CLASS = "Lcom/twitter/model/core/e;"

/**
 * Controls the icons at the top-right of every post header: the stock
 * Grok "analyse post" button and the two Muse buttons
 * ('ツイートのコピー' and 'スペースに転送').
 *
 * Timeline headers — one hook target, two tiny insertions in the Grok
 * button's bind consumer, Lcom/twitter/tweetview/core/ui/grok/e;.invoke:
 *  - head: a HeaderGrokButtons.attach* entry point — the tweet is
 *    state.a (p1, after a check-cast into a scratch register), the
 *    ImageView is this.a.a (delegate . image view). v0/v1 are written
 *    before any read in the original body, so clobbering them is safe.
 *  - tail (just before the only RETURN_OBJECT): syncVisibility when the
 *    Grok button stays, so the Muse buttons follow its per-bind
 *    visibility/tint; or syncVisibilityHideGrok when Grok is hidden, so
 *    the Muse buttons take over Grok's would-be visibility/tint and
 *    the Grok view itself is forced to GONE.
 *
 * Focal (post detail) headers have no Grok slot, so a resource patch
 * adds a slot row there (see FocalHeaderLayout) and the focal binder
 * hook calls the matching attachFocal* entry point. The Grok option
 * does not affect the focal slot.
 *
 * The whole feature is gated by the "postHeaderButtons" patch option
 * (default on): turning it off applies neither insertion and leaves
 * post headers completely stock.
 *
 * The three icons are then gated independently (merged v0.6.3, on top
 * of the user's copy-fix4 build):
 *  - "postHeaderGrokButton" (default OFF): off hides the Grok button;
 *    on shows it stock, with the Muse buttons packed to its left.
 *  - "postHeaderCopyButton" (default ON): shows 'ツイートのコピー'.
 *  - "postHeaderSaveButton" (default ON since v0.6.3; was OFF in the
 *    copy-fix4 build): shows 'スペースに転送' (it only copies the
 *    post URL).
 * Which attach* entry point is called is baked in from the copy/save
 * options: attach (both), attachCopyOnly, attachSaveOnly, attachNone —
 * and likewise attachFocal* for the focal header.
 *
 * This replaces the v0.6.0 modern-share-sheet placement, which targeted
 * the wrong surface and is removed.
 */
@Suppress("unused")
val postHeaderButtonsPatch =
    bytecodePatch(
        name = "Post header buttons",
        description = "Controls the post header icons: hide the Grok button and pack 'ツイートのコピー' / 'スペースに転送' in its place. Each icon can be shown or hidden in the patch options.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        val postHeaderButtons by booleanOption(
            key = "postHeaderButtons",
            default = true,
            title = "Enable post header icon controls",
            description = "Master switch for the post header icon settings below (Grok / Copy / Save). Turn this off to leave post headers completely stock.",
        )

        val postHeaderGrokButton by booleanOption(
            key = "postHeaderGrokButton",
            default = false,
            title = "Show the Grok button",
            description = "Off (default): the Grok 'analyse post' button is hidden and the Muse buttons below are packed in its place. On: the Grok button is shown as stock, with the Muse buttons to its left.",
        )

        val postHeaderCopyButton by booleanOption(
            key = "postHeaderCopyButton",
            default = true,
            title = "Show the 'ツイートのコピー' header button",
            description = "Shows the copy button in the post header, packed at the top-right of the post. Turn this off to remove only the copy button.",
        )

        val postHeaderSaveButton by booleanOption(
            key = "postHeaderSaveButton",
            default = true,
            title = "Show the 'スペースに転送' header button",
            description = "Shows the 'スペースに転送' button in the post header, packed next to the copy button. It only copies the post URL. Turn this off to remove only this button.",
        )

        extendWithBundled("extensions/muse-patches.dex")

        // ButtonPressHandler's Tweet entity must be generated for the taps.
        dependsOn(entityGenerator, displayedTranslationPatch)

        // The focal layout has no Grok slot. Leave all existing controls in place,
        // inserting a narrow slot between the author header and its next sibling.
        dependsOn(resourcePatch {
            execute {
                if (postHeaderButtons == true) addFocalHeaderSlot()
            }
        })

        execute {
            if (postHeaderButtons == true) {
                val fingerprint = GrokAnalyseBindFingerprint
                if (fingerprint.classDef.type != BIND_CLASS) {
                    throw PatchException(
                        "Grok analyse bind fingerprint matched an unexpected class: ${fingerprint.classDef.type}",
                    )
                }
                val method = fingerprint.method
                if (method.name != "invoke") {
                    throw PatchException(
                        "Grok analyse bind fingerprint matched an unexpected method: ${method.name}",
                    )
                }

                // ---- Head: attach (create/update the selected button(s)) ----
                // p1 is the state (core/z) typed as Object; refine it in a
                // scratch register before reading the tweet out of it.
                // Same register plan as v0.6.1; only the entry points'
                // names depend on the copy/save options.
                val showCopy = postHeaderCopyButton == true
                val showSave = postHeaderSaveButton == true
                val attachMethod =
                    when {
                        showCopy && showSave -> "attach"
                        showCopy -> "attachCopyOnly"
                        showSave -> "attachSaveOnly"
                        else -> "attachNone"
                    }
                val focalAttachMethod =
                    when {
                        showCopy && showSave -> "attachFocal"
                        showCopy -> "attachFocalCopyOnly"
                        showSave -> "attachFocalSaveOnly"
                        else -> "attachFocalNone"
                    }
                FocalHeaderBindFingerprint.method.addInstructions(
                    0,
                    """
                    iget-object v0, p2, Lcom/twitter/tweetview/core/ui/tweetheader/c;->a:Lcom/twitter/ui/tweet/TweetHeaderView;
                    invoke-static {v0, p1}, $HANDLER_CLASS->$focalAttachMethod(Landroid/view/View;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
                method.addInstructions(
                    0,
                    """
                    move-object v0, p1
                    check-cast v0, $STATE_CLASS
                    iget-object v1, v0, $STATE_CLASS->a:$TWEET_CLASS
                    iget-object v0, p0, $BIND_CLASS->a:$DELEGATE_CLASS
                    iget-object v0, v0, $DELEGATE_CLASS->a:Landroid/widget/ImageView;
                    invoke-static {v0, v1}, $HANDLER_CLASS->$attachMethod(Landroid/view/View;Ljava/lang/Object;)V
                    """.trimIndent(),
                )

                // ---- Tail: visibility sync (and Grok hide, if selected) ----
                val syncMethod =
                    if (postHeaderGrokButton == true) "syncVisibility"
                    else "syncVisibilityHideGrok"
                val returnIndex =
                    method.indexOfFirstInstructionReversedOrThrow(Opcode.RETURN_OBJECT)

                method.addInstructions(
                    returnIndex,
                    """
                    iget-object v0, p0, $BIND_CLASS->a:$DELEGATE_CLASS
                    iget-object v0, v0, $DELEGATE_CLASS->a:Landroid/widget/ImageView;
                    invoke-static {v0}, $HANDLER_CLASS->$syncMethod(Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }
    }
