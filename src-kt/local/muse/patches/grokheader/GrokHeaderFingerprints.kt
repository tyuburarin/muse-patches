package local.muse.patches.grokheader

import app.morphe.patcher.Fingerprint

/**
 * The bind consumer of the Grok "analyse post" header button:
 * Lcom/twitter/tweetview/core/ui/grok/e;.invoke(Ljava/lang/Object;)Ljava/lang/Object;
 *
 * Verified against X 12.19.1 (classes16): registers 7, ins 2
 * (p0 = this, the consumer holding the view delegate in field a;
 * p1 = the tweetview state Lcom/twitter/tweetview/core/z;, whose field a
 * is the tweet Lcom/twitter/model/core/e;). The method sets the Grok
 * ImageView's image (0x7f08063e), tint and visibility, and has a single
 * RETURN_OBJECT at its end. It contains the distinctive feature-flag
 * string below.
 */
internal object GrokAnalyseBindFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/ui/grok/e;",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("grok_android_analyze_ads_enabled"),
)

internal object FocalHeaderBindFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/focal/ui/tweetheader/FocalTweetHeaderViewDelegateBinder;",
    name = "d",
    returnType = "V",
    parameters = listOf(
        "Lcom/twitter/model/core/e;",
        "Lcom/twitter/tweetview/core/ui/tweetheader/c;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
)
