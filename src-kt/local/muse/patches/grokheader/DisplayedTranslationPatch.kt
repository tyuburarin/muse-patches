package local.muse.patches.grokheader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import local.muse.patches.entity.entityGenerator
import local.muse.utils.extendWithBundled

private class TranslationRenderFingerprint(type: String) : Fingerprint(
    definingClass = type,
    name = "invoke",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
)

private val focalTranslation = TranslationRenderFingerprint(
    "Lcom/twitter/tweetview/focal/ui/translation/f0;",
)
private val focalGrokTranslation = TranslationRenderFingerprint(
    "Lcom/twitter/tweetview/focal/ui/translation/b;",
)
private val automaticTranslation = TranslationRenderFingerprint(
    "Lcom/twitter/tweetview/core/ui/translation/m;",
)

internal val displayedTranslationPatch = bytecodePatch {
    dependsOn(entityGenerator)
    extendWithBundled("extensions/muse-patches.dex")
    execute {
        listOf(focalTranslation, focalGrokTranslation, automaticTranslation).forEach { fingerprint ->
            val handler = if (fingerprint === automaticTranslation) "trackAutomatic" else "trackFocal"
            // Does not clobber any registers or change X's translation rendering.
            fingerprint.method.addInstructions(
                0,
                "invoke-static {p0, p1}, Llocal/muse/extension/twitter/patches/nativeFeatures/DisplayedTranslation;->$handler(Ljava/lang/Object;Ljava/lang/Object;)V",
            )
        }
    }
}
