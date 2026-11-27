package local.muse.extension.twitter.patches.nativeFeatures;

import android.content.Context;
import android.graphics.ColorFilter;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import local.muse.extension.twitter.Pref;

/**
 * Runtime side of the "Post header buttons" patch (merged v0.6.3:
 * copy-fix4 base + per-icon Grok/Copy/Save options).
 *
 * Which of the three header icons — Grok (stock), Copy, Save-to-Muse —
 * are shown is chosen at patch time in Morphe via three independent
 * patch options (postHeaderGrokButton / postHeaderCopyButton /
 * postHeaderSaveButton); the patch bakes the choice in by calling a
 * different entry point here.
 *
 * Timeline headers — hooks Lcom/twitter/tweetview/core/ui/grok/e;.invoke
 * (the bind consumer of the Grok "analyse post" header button):
 *  - head: attach / attachCopyOnly / attachSaveOnly / attachNone —
 *    finds (or creates) the selected Muse ImageView buttons in the
 *    Grok view's parent (the horizontal header row), packed immediately
 *    left of the Grok button in copy, save order, removes any
 *    unselected Muse button, and (re)binds them to the current tweet.
 *  - tail: syncVisibility when the Grok button stays — the Muse
 *    buttons copy its final visibility/tint for this bind; or
 *    syncVisibilityHideGrok when Grok is hidden — the Muse buttons
 *    take over the Grok button's would-be visibility/tint and the Grok
 *    view itself is then forced to GONE, so the Muse buttons stay
 *    packed at the right end of the header on exactly the posts where
 *    Grok would have appeared.
 *
 * Focal (post detail) headers have no Grok slot; the patch's resource
 * part adds a slot row with a GONE sizing anchor
 * (R.id.muse_focal_header_anchor, dimensions copied from the timeline
 * Grok slot) and the focal binder hook calls attachFocal /
 * attachFocalCopyOnly / attachFocalSaveOnly / attachFocalNone, which
 * place the selected Muse buttons in that slot the same way. The Grok
 * show/hide option does not affect the focal slot (it has no Grok
 * button); with both Muse buttons off the slot row stays GONE.
 *
 * The buttons reuse the reference view's LayoutParams (size, margins,
 * gravity), padding and scale type, so they match the neighbouring
 * header icons. Icons are the same stock drawables the legacy
 * share-menu entries use (ic_vector_copy_stroke /
 * ic_vector_bookmark_stroke), resolved by name at runtime. Taps go to
 * ButtonPressHandler.buttonPressAction(context, action, tweet, view)
 * — the 4-arg form, so the displayed-translation copy (copy-fix4) can
 * find the rendered translation from the source view.
 *
 * Tweet views are recycled, so attach*() is idempotent: buttons are
 * created once per header row and their bound tweet is replaced on
 * every bind. Every entry point swallows all throwables: a failure
 * here must never break a tweet bind.
 */
public class HeaderGrokButtons {

    private static final String TAG = "MusePatches";

    private static final String ID_TAG_COPY = "muse_header_copy";
    private static final String ID_TAG_SAVE = "muse_header_save";

    // Keyed-tag slot holding the currently bound tweet on each button.
    // 0x7f0b13e9 is R.id.tweet_grok_analyse_post of the host app; our own
    // views never use it for anything else.
    private static final int TWEET_TAG_KEY = 0x7f0b13e9;

    private static final String COPY_ICON = "ic_vector_copy_stroke";
    private static final String SAVE_ICON = "ic_vector_bookmark_stroke";
    private static final String COPY_DESC = "ツイートのコピー";
    private static final String SAVE_DESC = "スペースに転送";

    /** Both Muse buttons (copy option on, save option on). */
    public static void attach(View grokView, Object tweet) {
        attachInternal(grokView, tweet, true, true);
    }

    /** Copy button only (copy option on, save option off). */
    public static void attachCopyOnly(View grokView, Object tweet) {
        attachInternal(grokView, tweet, true, false);
    }

    /** Save button only (copy option off, save option on). */
    public static void attachSaveOnly(View grokView, Object tweet) {
        attachInternal(grokView, tweet, false, true);
    }

    /** No Muse buttons (both options off — e.g. only Grok is hidden). */
    public static void attachNone(View grokView, Object tweet) {
        attachInternal(grokView, tweet, false, false);
    }

    /** Focal slot, both Muse buttons. */
    public static void attachFocal(View header, Object tweet) {
        attachFocalInternal(header, tweet, true, true);
    }

    /** Focal slot, copy button only. */
    public static void attachFocalCopyOnly(View header, Object tweet) {
        attachFocalInternal(header, tweet, true, false);
    }

    /** Focal slot, save button only. */
    public static void attachFocalSaveOnly(View header, Object tweet) {
        attachFocalInternal(header, tweet, false, true);
    }

    /** Focal slot, no Muse buttons: the slot row stays gone. */
    public static void attachFocalNone(View header, Object tweet) {
        attachFocalInternal(header, tweet, false, false);
    }

    private static void attachFocalInternal(View header, Object tweet,
                                            boolean showCopy, boolean showSave) {
        try {
            if (header == null || !(header.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) header.getParent();
            Context context = header.getContext();
            int id = context.getResources().getIdentifier(
                    "muse_focal_header_anchor", "id", context.getPackageName());
            if (id == 0) return;
            View anchor = parent.findViewById(id);
            if (!(anchor instanceof ImageView)) return;
            TypedValue color = new TypedValue();
            int attr = context.getResources().getIdentifier(
                    "coreColorSecondaryText", "attr", context.getPackageName());
            if (context.getTheme().resolveAttribute(attr, color, true)) {
                int tint = color.resourceId != 0
                        ? context.getResources().getColor(color.resourceId, context.getTheme()) : color.data;
                ((ImageView) anchor).setColorFilter(tint);
            }
            attachInternal(anchor, tweet, showCopy, showSave);
            ViewGroup row = (ViewGroup) anchor.getParent();
            if (!showCopy && !showSave) {
                row.setVisibility(View.GONE);
                return;
            }
            row.setVisibility(Pref.postHeaderButtons() && tweet != null ? View.VISIBLE : View.GONE);
            // The sizing anchor stays gone; only the existing Muse buttons occupy space.
            for (int i = 0; i < row.getChildCount(); i++) {
                View button = row.getChildAt(i);
                if (ID_TAG_COPY.equals(button.getTag()) || ID_TAG_SAVE.equals(button.getTag())) {
                    button.setVisibility(View.VISIBLE);
                    if (button instanceof ImageView) {
                        ((ImageView) button).setColorFilter(((ImageView) anchor).getColorFilter());
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "focal header attach failed", t);
        }
    }

    private static void attachInternal(View grokView, Object tweet,
                                       boolean showCopy, boolean showSave) {
        try {
            if (grokView == null || tweet == null) return;
            if (!Pref.postHeaderButtons()) return;
            ViewParent parent = grokView.getParent();
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup row = (ViewGroup) parent;

            ImageView copy = findTagged(row, ID_TAG_COPY);
            ImageView save = findTagged(row, ID_TAG_SAVE);

            // Drop buttons the current configuration does not select, so a
            // recycled row never keeps a stale one.
            if (!showCopy && copy != null) { row.removeView(copy); copy = null; }
            if (!showSave && save != null) { row.removeView(save); save = null; }
            if (!showCopy && !showSave) return;

            if (showCopy && copy == null) {
                copy = makeButton(grokView, COPY_ICON, COPY_DESC, ID_TAG_COPY, "CopyPostText");
            }
            if (showSave && save == null) {
                save = makeButton(grokView, SAVE_ICON, SAVE_DESC, ID_TAG_SAVE, "SaveToMuse");
            }

            // Desired packing, left to right immediately left of the
            // reference view (the Grok button, or the focal anchor).
            List<ImageView> wanted = new ArrayList<>();
            if (showCopy) wanted.add(copy);
            if (showSave) wanted.add(save);

            int g = row.indexOfChild(grokView);
            if (g < 0) return;
            boolean packed = g >= wanted.size();
            if (packed) {
                for (int i = 0; i < wanted.size(); i++) {
                    if (row.getChildAt(g - wanted.size() + i) != wanted.get(i)) {
                        packed = false;
                        break;
                    }
                }
            }
            if (!packed) {
                for (ImageView b : wanted) row.removeView(b);
                int idx = row.indexOfChild(grokView);
                if (idx < 0) return;
                for (int i = 0; i < wanted.size(); i++) {
                    row.addView(wanted.get(i), idx + i);
                }
            }
            for (ImageView b : wanted) b.setTag(TWEET_TAG_KEY, tweet);
        } catch (Throwable t) {
            Log.e(TAG, "header attach failed", t);
        }
    }

    /**
     * Called from the tail of grok/e.invoke(state) when the Grok button
     * stays visible, after the stock code has set its image, tint and
     * visibility for this bind: the Muse buttons mirror it.
     */
    public static void syncVisibility(View grokView) {
        syncMuseButtons(grokView, false);
    }

    /**
     * Called from the tail of grok/e.invoke(state) when the Grok button
     * is hidden by option: the Muse buttons take over the Grok button's
     * would-be visibility/tint for this bind, then the Grok view itself
     * is forced to GONE.
     */
    public static void syncVisibilityHideGrok(View grokView) {
        syncMuseButtons(grokView, true);
    }

    private static void syncMuseButtons(View grokView, boolean hideGrok) {
        try {
            if (grokView == null) return;
            ViewParent parent = grokView.getParent();
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup row = (ViewGroup) parent;
            int vis = grokView.getVisibility();
            ColorFilter tint = grokView instanceof ImageView
                    ? ((ImageView) grokView).getColorFilter() : null;
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                Object tag = child.getTag();
                if (ID_TAG_COPY.equals(tag) || ID_TAG_SAVE.equals(tag)) {
                    child.setVisibility(vis);
                    if (tint != null && child instanceof ImageView) {
                        ((ImageView) child).setColorFilter(tint);
                    }
                }
            }
            if (hideGrok) grokView.setVisibility(View.GONE);
        } catch (Throwable t) {
            Log.e(TAG, "header syncVisibility failed", t);
        }
    }

    private static ImageView findTagged(ViewGroup row, String tag) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (tag.equals(child.getTag()) && child instanceof ImageView) {
                return (ImageView) child;
            }
        }
        return null;
    }

    private static ImageView makeButton(View grokView, String iconName, String description,
                                        String idTag, final String action) {
        Context context = grokView.getContext();
        ImageView button = new ImageView(context);
        button.setLayoutParams(copyLayoutParams(grokView));
        int iconId = context.getResources()
                .getIdentifier(iconName, "drawable", context.getPackageName());
        if (iconId != 0) button.setImageResource(iconId);
        if (grokView instanceof ImageView) {
            ImageView grok = (ImageView) grokView;
            button.setScaleType(grok.getScaleType());
            ColorFilter tint = grok.getColorFilter();
            if (tint != null) button.setColorFilter(tint);
        }
        button.setPadding(grokView.getPaddingLeft(), grokView.getPaddingTop(),
                grokView.getPaddingRight(), grokView.getPaddingBottom());
        button.setContentDescription(description);
        button.setTag(idTag);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Object tweet = v.getTag(TWEET_TAG_KEY);
                    if (tweet == null) return;
                    ButtonPressHandler.buttonPressAction(v.getContext(), action, tweet, v);
                } catch (Throwable t) {
                    Log.e(TAG, "header button action failed", t);
                }
            }
        });
        return button;
    }

    /** Clone the reference view's layout params so size/margins/gravity match. */
    private static ViewGroup.LayoutParams copyLayoutParams(View grokView) {
        ViewGroup.LayoutParams src = grokView.getLayoutParams();
        if (src instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams s = (LinearLayout.LayoutParams) src;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (ViewGroup.MarginLayoutParams) s);
            lp.gravity = s.gravity;
            lp.weight = s.weight;
            return lp;
        }
        if (src instanceof ViewGroup.MarginLayoutParams) {
            return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) src);
        }
        if (src != null) {
            return new ViewGroup.LayoutParams(src.width, src.height);
        }
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
