/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package local.muse.patches.entity

import local.muse.utils.extendWithBundled
import local.muse.patches.entity.extMedia.extMediaEntityPatch
import local.muse.patches.entity.tweet.tweetEntityPatch
import local.muse.patches.entity.tweetInfo.tweetInfoEntityPatch
import app.morphe.patcher.patch.bytecodePatch

val entityGenerator =
    bytecodePatch(
        description = "Reflect entity patch generator",
    ) {
        extendWithBundled("extensions/muse-patches.dex")

        dependsOn(tweetEntityPatch, tweetInfoEntityPatch, extMediaEntityPatch)
    }
