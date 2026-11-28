package local.muse.extension.twitter.patches.nativeFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import local.muse.extension.crimera.PikoUtils;
import local.muse.extension.twitter.entity.Media;
import local.muse.extension.twitter.entity.Tweet;

public class ButtonPressHandler {

    private static final String TAG_COPY = "CopyPostText";
    private static final String TAG_SAVE = "SaveToMuse";
    private static final String TAG_CLEAN = "CleanAffiliateLink";

    public static boolean isButtonPressed(Object buttonPressed) {
        String tag = String.valueOf(buttonPressed);
        return TAG_COPY.equals(tag) || TAG_SAVE.equals(tag) || TAG_CLEAN.equals(tag);
    }

    public static void buttonPressAction(Context context, Object buttonPressed, Object tweetObject) {
        buttonPressAction(context, buttonPressed, tweetObject, null);
    }

    public static void buttonPressAction(Context context, Object buttonPressed, Object tweetObject, View source) {
        String tag = String.valueOf(buttonPressed);
        try {
            PikoUtils.init(context);
            Tweet tweet = new Tweet(tweetObject);
            if (TAG_COPY.equals(tag)) {
                copyPostText(context, tweet, source);
            } else if (TAG_SAVE.equals(tag)) {
                forwardToSpace(context, tweet);
            } else if (TAG_CLEAN.equals(tag)) {
                // Works on the raw tweet item: the URL entities are
                // collected reflectively from its object graph.
                local.muse.extension.twitter.patches.links.AffiliateMenuHandler
                        .onCleanLinkPressed(context, tweetObject);
            }
        } catch (Exception e) {
            Log.e("MusePatches", "share menu action failed", e);
        }
    }

    private static void copyPostText(Context context, Tweet tweet, View source) throws Exception {
        String translated = null;
        try {
            translated = DisplayedTranslation.getText(tweet.getTweetId(), source);
        } catch (Exception ignored) {
        }
        String payload = buildCopyPayload(tweet, translated);
        if (payload == null) {
            Toast.makeText(context, "コピーできる内容がありません", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Post", payload));
        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show();
    }

    private static void forwardToSpace(Context context, Tweet tweet) throws Exception {
        Long id = tweet.getTweetId();
        if (id == null) return;
        String postUrl = "https://x.com/i/status/" + id;
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Post URL", postUrl));
        Toast.makeText(context, "ポストのURLをコピーしました", Toast.LENGTH_SHORT).show();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("hatch://space/xposutozhi-kichang"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException e) {
            // Keep the copied URL available even if Muse cannot handle the link.
            Log.e("MusePatches", "could not open Muse space; post URL is copied", e);
            Toast.makeText(context, "Museを開けませんでした。URLはコピー済みです",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static String buildCopyPayload(Tweet tweet, String translated) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Author line.
        try {
            String name = tweet.getTweetProfileName();
            String handle = tweet.getTweetUsername();
            if (name != null && handle != null) {
                sb.append(name).append('@').append(handle);
                Long id = tweet.getTweetId();
                if (id != null) {
                    long epochMs = (id >> 22) + 1288834974657L;
                    String when = new SimpleDateFormat("M/d HH:mm", Locale.US)
                            .format(new Date(epochMs));
                    sb.append(' ').append(when);
                }
                sb.append('\n');
            }
        } catch (Exception ignored) {
        }

        // Tweet#getText() returns the long (note tweet) text when present,
        // otherwise the regular text, with t.co / pic.x.com links stripped.
        String text = translated == null ? tweet.getText() : translated
                .replaceAll("pic\\.x\\.com/\\S+", "")
                .replaceAll("https?://t\\.co/\\S+", "");
        boolean hasText = text != null && !text.trim().isEmpty();
        if (hasText) {
            sb.append(text);
        }

        // Media URLs, one per line.
        boolean hasMedia = false;
        try {
            ArrayList<ArrayList<Media>> mediaGroups = tweet.getMediaList();
            for (ArrayList<Media> group : mediaGroups) {
                for (Media media : group) {
                    if (media.url == null || media.url.isEmpty()) continue;
                    String url = media.type == 0 ? media.url.split("\\?")[0] : media.url;
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(url);
                    hasMedia = true;
                }
            }
        } catch (Exception ignored) {
        }

        if (!hasText && !hasMedia) return null;
        return sb.toString();
    }
}
