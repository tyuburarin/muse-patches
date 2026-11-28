package local.muse.patches.sharemenu

import local.muse.patches.MuseConstants.COMPATIBILITY_X
import local.muse.patches.entity.entityGenerator
import local.muse.patches.sharemenu.hooks.shareMenuButtonInjection
import local.muse.patches.sharemenu.hooks.shareMenuButtonOnClickHook
import local.muse.utils.extendWithBundled
import local.muse.patches.grokheader.displayedTranslationPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val copyPostTextSharePatch =
    bytecodePatch(
        name = "Copy post text",
        description = "Adds a 'ツイートのコピー' entry to the post share menu that copies the full text of the post (including long posts) to the clipboard.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        extendWithBundled("extensions/muse-patches.dex")

        dependsOn(shareMenuButtonOnClickHook, entityGenerator, displayedTranslationPatch)

        execute {
            shareMenuButtonInjection(
                actionName = "CopyPostText",
                prefFunctionName = "copyPostTextButton",
                stringId = "ツイートのコピー",
                iconId = "ic_vector_copy_stroke",
            )
        }
    }

@Suppress("unused")
val cleanAffiliateLinkSharePatch =
    bytecodePatch(
        name = "Clean affiliate link button",
        description = "Adds a 'アフィ外して開く' entry to the post share menu that fully resolves the post's links (short/redirector hops included), strips the affiliate parts and opens the first one. The explicit, full-strength counterpart to the automatic local-only cleaning.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        extendWithBundled("extensions/muse-patches.dex")

        dependsOn(shareMenuButtonOnClickHook, entityGenerator)

        execute {
            shareMenuButtonInjection(
                actionName = "CleanAffiliateLink",
                prefFunctionName = "cleanAffiliateLinkButton",
                stringId = "アフィ外して開く",
                iconId = "ic_vector_link",
            )
        }
    }

@Suppress("unused")
val saveToMuseSharePatch =
    bytecodePatch(
        name = "Save to Muse",
        description = "Adds a 'スペースに転送' entry to the post share menu that copies the post URL so it can be clipped into the Muse clip space.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        extendWithBundled("extensions/muse-patches.dex")

        dependsOn(shareMenuButtonOnClickHook, entityGenerator)

        execute {
            shareMenuButtonInjection(
                actionName = "SaveToMuse",
                prefFunctionName = "saveToMuseButton",
                stringId = "スペースに転送",
                iconId = "ic_vector_bookmark_stroke",
            )
        }
    }
