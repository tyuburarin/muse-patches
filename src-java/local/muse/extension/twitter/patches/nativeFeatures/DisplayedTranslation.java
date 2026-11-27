package local.muse.extension.twitter.patches.nativeFeatures;

import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

import local.muse.extension.twitter.entity.Tweet;

/** X 12.19.1 render bindings only; never a translation cache keyed by tweet ID. */
public final class DisplayedTranslation {
    private static final String LOG_TAG = "MuseTranslation";
    private static final Map<TextView, Binding> bindings = new WeakHashMap<>();

    private static final class Binding {
        final Long id;
        final String text;
        Binding(Long id, String text) { this.id = id; this.text = text; }
    }

    private static Object field(Object object, String name) throws Exception {
        return object.getClass().getField(name).get(object);
    }

    private static boolean flag(Object object, String name) throws Exception {
        return Boolean.TRUE.equals(field(object, name));
    }

    // These field paths and render conditions are verified against the target APK.
    // Called before X reuses p1 for scratch values; only records the incoming state.
    public static void trackFocal(Object consumer, Object state) {
        TextView view = null;
        try {
            boolean grok = state.getClass().getName().equals(
                    "com.twitter.tweetview.focal.ui.translation.v");
            view = (TextView) field(field(consumer, "b"), grok ? "b" : "c");
            Object tweet = field(state, grok ? "f" : "h");
            Object translation = field(state, grok ? "g" : "d");
            boolean shown = flag(state, "a") && (grok
                    ? flag(state, "b") && translation != null && translation.getClass().getName()
                        .equals("com.twitter.translation.u0$d")
                    : !flag(state, "b") && flag(state, "c") && translation != null);
            String text = shown ? (String) field(field(translation, grok ? "g" : "e"), "a") : null;
            bind(view, tweet, text);
            logBinding(grok ? "focal-grok" : "focal-standard", view);
        } catch (Exception e) {
            // A failed rebind must invalidate any text belonging to the previous post.
            if (view != null) bindings.remove(view);
            log("hook=focal failure=" + e.getClass().getSimpleName());
        }
    }

    public static void trackAutomatic(Object consumer, Object pair) {
        TextView view = null;
        try {
            view = (TextView) field(field(consumer, "a"), "d");
            Object state = field(pair, "a");
            Object tweet = field(state, "a");
            Object model = field(tweet, "a");
            Object translation = model.getClass().getMethod("b").invoke(model);
            // Copy the model's full translation even while X renders its preview.
            // Missing/blank full text falls back to the original via getText();
            // never substitute the partial preview or request another translation.
            Object content = translation == null ? null : field(translation, "c");
            bind(view, tweet, content == null ? null : (String) field(content, "a"));
            // X's separate s.invoke handles Show original by hiding this TextView;
            // getText still checks isShown() at the instant the button is pressed.
            logBinding("automatic-full", view);
        } catch (Exception e) {
            if (view != null) bindings.remove(view);
            log("hook=automatic failure=" + e.getClass().getSimpleName());
        }
    }

    private static void bind(TextView view, Object tweet, String text) throws Exception {
        bindings.put(view, new Binding(tweet == null ? null : new Tweet(tweet).getTweetId(), text));
    }

    // Opt-in diagnostics: adb shell setprop log.tag.MuseTranslation DEBUG
    // Never log post text, author information or the clipboard payload.
    private static void log(String message) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, message);
    }

    private static void logBinding(String hook, TextView view) {
        if (!Log.isLoggable(LOG_TAG, Log.DEBUG)) return;
        Binding binding = bindings.get(view);
        log("hook=" + hook + " id=" + binding.id
                + " chars=" + (binding.text == null ? 0 : binding.text.length()));
    }

    private static String selection(Long id, String reason, String text) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            log("copy id=" + id + " decision=" + reason
                    + " chars=" + (text == null ? 0 : text.length()));
        }
        return text;
    }

    private static View tweetRoot(View view) {
        while (view != null) {
            String type = view.getClass().getName();
            // X 12.19.1 uses a separate LinearLayout host for timeline posts.
            // Stop at the nearest verified post host, including nested quotes.
            if (type.equals("com.twitter.tweetview.core.TweetView")
                    || type.equals("com.twitter.tweetview.core.LinearLayoutTweetView")) return view;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    public static String getText(Long id, View source) {
        if (id == null) return selection(id, "missing-id", null);
        View root = source == null ? null : tweetRoot(source);
        if (source != null && root == null) return selection(id, "source-root-missing", null);
        String result = null;
        View matchedRoot = null;
        int idMatches = 0, visibleMatches = 0, ownerMatches = 0;
        for (Map.Entry<TextView, Binding> entry : bindings.entrySet()) {
            TextView view = entry.getKey();
            Binding binding = entry.getValue();
            if (view == null || !id.equals(binding.id)) continue;
            idMatches++;
            if (!view.isShown()) continue;
            visibleMatches++;
            View owner = tweetRoot(view);
            if (owner == null || (root != null && owner != root)) continue;
            ownerMatches++;
            // Share-menu calls lack a source view: multiple visible instances are ambiguous.
            if (matchedRoot != null && matchedRoot != owner) return selection(id, "ambiguous-roots", null);
            matchedRoot = owner;
            if (binding.text == null || binding.text.trim().isEmpty()) return selection(id, "no-translation-text", null);
            if (result != null && !result.equals(binding.text)) return selection(id, "ambiguous-text", null);
            result = binding.text;
        }
        return selection(id, result != null ? "translated"
                : "fallback bindings=" + bindings.size() + " idMatches=" + idMatches
                    + " visibleMatches=" + visibleMatches + " ownerMatches=" + ownerMatches, result);
    }
}
