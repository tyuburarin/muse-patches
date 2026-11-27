/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package local.muse.patches.entity.extMedia

import local.muse.utils.extendWithBundled
import local.muse.utils.changeFirstString
import local.muse.utils.changeStringAt
import local.muse.utils.getFieldName
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import local.muse.utils.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val extMediaEntityPatch =
    bytecodePatch(
        description = "For extended media entity reflection",
    ) {
        extendWithBundled("extensions/muse-patches.dex")

        execute {

            MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.method.apply {
                val firstIGetObjectIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                val videoInfoFieldName =
                    MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.getFieldName(
                        firstIGetObjectIndex,
                    )
                ExtMediaGetVideosFingerprint.changeFirstString(videoInfoFieldName)

                val secondIGetObjectIndex = indexOfFirstInstruction(firstIGetObjectIndex + 1, Opcode.IGET_OBJECT)
                val videoVariantsFieldName =
                    MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.getFieldName(
                        secondIGetObjectIndex,
                    )
                ExtMediaGetVideosFingerprint.changeStringAt(1, videoVariantsFieldName)
            }

            // ------------
            val imageFieldName =
                ExtMediaGetImageMethodFinder.getFieldName(
                    ExtMediaGetImageMethodFinder.method
                        .instructions
                        .last {
                            it.opcode ==
                                Opcode.IGET_OBJECT
                        }.location.index,
                )
            ExtMediaGetImageFingerprint.changeFirstString(imageFieldName)
// ------------
            val mediaResolutionFieldName =
                ExtMediaGetSensitiveMediaCategoriesFingerprint.classDef.fields
                    .first {
                        it.type ==
                            MediaResolutionToStringFingerprint.classDef.type
                    }.name

            ExtMediaHighResolutionFingerprint.changeFirstString(mediaResolutionFieldName)
        }
    }
