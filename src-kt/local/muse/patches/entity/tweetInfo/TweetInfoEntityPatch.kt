/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package local.muse.patches.entity.tweetInfo

import local.muse.utils.extendWithBundled
import local.muse.utils.changeFirstString
import local.muse.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch

val tweetInfoEntityPatch =
    bytecodePatch(
        description = "For tweet info entity reflection",
    ) {
        extendWithBundled("extensions/muse-patches.dex")

        execute {
            TweetInfoObjectFingerprint.apply {
                val langStrIndex = stringMatches[1].index
                method.apply {
                    val fieldName = getInstruction(langStrIndex + 1).fieldExtractor().name
                    TweetLangFingerprint.changeFirstString(fieldName)
                }
            }
        }
    }
