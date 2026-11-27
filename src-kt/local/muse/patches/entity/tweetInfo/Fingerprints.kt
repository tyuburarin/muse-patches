/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package local.muse.patches.entity.tweetInfo

import local.muse.patches.MuseConstants.ENTITY_DESCRIPTOR
import app.morphe.patcher.Fingerprint

private const val ENTITY_TWEET_INFO_DEFINING_CLASS = "${ENTITY_DESCRIPTOR}TweetInfo"

// Needed for Tweet entity
object TweetInfoObjectFingerprint : Fingerprint(
    strings =
        listOf(
            "flags",
            "lang",
            "supplemental_language",
        ),
    definingClass = "Lcom/twitter/database/legacy/tdbh/",
)

internal object TweetLangFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_INFO_DEFINING_CLASS,
    name = "getLang",
)
