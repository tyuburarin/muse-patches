package local.muse.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object MuseConstants {
    val COMPATIBILITY_X =
        Compatibility(
            name = "Twitter",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets =
                listOf(
                    AppTarget(
                        version = "12.19.1-release.0",
                    ),
                ),
        )

    const val INTEGRATIONS_PACKAGE = "Llocal/muse/extension/twitter"
    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val ENTITY_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/entity/"
    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    const val STRING_REF_DESCRIPTOR = "Llocal/muse/extension/shared/StringRef"
    const val NATIVE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/nativeFeatures"
}


