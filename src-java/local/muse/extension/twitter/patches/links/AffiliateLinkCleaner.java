package local.muse.extension.twitter.patches.links;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Strip affiliate links" — cleans the URL of every link entity in a post
 * before the user ever taps it.
 *
 * Hooked from the patch side into
 * Lcom/twitter/model/json/core/JsonUrlEntity$$JsonObjectMapper;->parse,
 * right before the parsed entity is returned. At that point the entity
 * still carries the raw API fields (verified against X 12.19.1):
 *
 *   b = url_https (t.co), c = expanded_url, d = display_url, e = url (t.co)
 *
 * The expanded URL is cleaned and written back into c, and also into
 * b/e — the fields the app actually opens — so a tap goes straight to
 * the cleaned destination instead of through t.co with the affiliate
 * parts still attached (the same trick Piko's "No shortened URL" uses,
 * entity.e = entity.c).
 *
 * Cleaning rules are a port of the user's アフィリンク外し Space
 * (server/src/actions.ts), so both tools agree:
 *  - Amazon with an ASIN: normalised to https://<host>/dp/<ASIN>,
 *    every query param dropped.
 *  - Amazon without an ASIN: tag/th/psc and the pd_rd_/pf_rd_ families
 *    plus generic affiliate/tracking params dropped, path kept.
 *  - Other shops: only known affiliate/tracking params dropped
 *    (aff*, utm_*, click ids, ...); scid is dropped only when its value
 *    marks it as an affiliate id; product-identifying params
 *    (id, item_id, sku, color, ...) are never dropped.
 *  - x.com / twitter.com links are left completely alone.
 *
 * Short links whose affiliate payload lives behind a redirect
 * (amzn.to, a.r10.to, hb.afl.rakuten.co.jp, bit.ly, ...) are resolved
 * first: redirects are followed manually (no cookies are stored, the
 * merchant page itself is never fetched — following stops at the
 * first non-redirect response), the final URL is then cleaned.
 * Results, including failures, are cached for the process lifetime
 * so timeline re-parses never repeat the network work. Resolution
 * runs on the API deserialisation thread (JSON parsing is off the UI
 * thread); a dead short link costs at most the connect/read timeout.
 *
 * Everything fails open: any error leaves the entity untouched.
 */
public class AffiliateLinkCleaner {

    // ---- JsonUrlEntity field names (X 12.19.1, verified from the dex) ----
    private static final String FIELD_URL_HTTPS = "b";
    private static final String FIELD_EXPANDED = "c";
    private static final String FIELD_URL = "e";

    private static final Pattern AMAZON_HOST =
            Pattern.compile("(^|\\.)amazon\\.[a-z.]+$");
    private static final Pattern ASIN_IN_PATH =
            Pattern.compile(
                    "/(?:dp|gp/product|gp/aw/d|product)/([A-Z0-9]{10})(?=[/?#]|$)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ASIN_OBIDOS =
            Pattern.compile("/exec/obidos/asin/([A-Z0-9]{10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCID_AFFILIATE =
            Pattern.compile("(^|[_-])af([_-]|$)|aff", Pattern.CASE_INSENSITIVE);

    private static final Set<String> AFFILIATE_PARAMS = new HashSet<>();
    private static final Set<String> TRACKING_PARAMS = new HashSet<>();
    private static final Set<String> PRODUCT_PARAMS = new HashSet<>();
    private static final Set<String> SHORT_HOSTS = new HashSet<>();

    static {
        String[] affiliate = {
            "aff", "affiliate", "affiliate_id", "affiliateid", "aff_id",
            "affid", "affuid", "a8", "vc_url", "linkcode", "linkid",
        };
        for (String s : affiliate) AFFILIATE_PARAMS.add(s);

        String[] tracking = {
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
            "twclid", "ttclid", "yclid", "li_fat_id", "mc_cid", "mc_eid",
            "igshid", "_ga", "_gl", "spm", "scm", "ref", "ref_", "referrer",
        };
        for (String s : tracking) TRACKING_PARAMS.add(s);

        String[] product = {
            "id", "item", "item_id", "itemid", "pid", "product",
            "product_id", "productid", "jan", "sku", "code", "goods",
            "goods_id", "shop", "shop_id", "color", "size", "variant",
            "quantity", "q", "keyword", "s", "k",
        };
        for (String s : product) PRODUCT_PARAMS.add(s);

        // Shorteners / affiliate redirectors: the destination (and its
        // affiliate payload) only exists behind a redirect.
        String[] shorts = {
            "amzn.to", "a.co", "amzn.asia",
            "a.r10.to", "hb.afl.rakuten.co.jp",
            "bit.ly", "tinyurl.com", "rb.gy", "ow.ly", "buff.ly",
            "cutt.ly", "is.gd", "goo.gl",
            "rpx.a8.net", "ck.jp.ap.valuecommerce.com", "h.accesstrade.net",
        };
        for (String s : shorts) SHORT_HOSTS.add(s);
    }

    private static final ConcurrentHashMap<String, String> RESOLVE_CACHE =
            new ConcurrentHashMap<>();

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    // ------------------------------------------------------------------
    // Entry point called from the patched parse method.
    // ------------------------------------------------------------------

    public static void clean(Object entity) {
        try {
            if (entity == null) return;
            String expanded = getField(entity, FIELD_EXPANDED);
            String url = getField(entity, FIELD_URL);
            String target = expanded != null && !expanded.isEmpty() ? expanded : url;
            if (target == null || target.isEmpty()) return;

            String resolved = resolveIfShort(target);
            String cleaned = cleanUrl(resolved);
            if (cleaned == null || cleaned.equals(target)) {
                // Nothing to strip; still, if a short link resolved to a
                // clean-but-different destination, open that directly.
                if (!resolved.equals(target)) {
                    setField(entity, FIELD_EXPANDED, resolved);
                    setField(entity, FIELD_URL, resolved);
                    setField(entity, FIELD_URL_HTTPS, resolved);
                }
                return;
            }
            setField(entity, FIELD_EXPANDED, cleaned);
            setField(entity, FIELD_URL, cleaned);
            setField(entity, FIELD_URL_HTTPS, cleaned);
        } catch (Throwable ignored) {
            // Fail open: a cleaning bug must never break link parsing.
        }
    }

    private static String getField(Object entity, String name) {
        try {
            Field f = findField(entity.getClass(), name);
            if (f == null) return null;
            Object v = f.get(entity);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setField(Object entity, String name, String value) {
        try {
            Field f = findField(entity.getClass(), name);
            if (f != null) f.set(entity, value);
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Cleaning (port of the アフィリンク外し Space rules)
    // ------------------------------------------------------------------

    static String cleanUrl(String rawUrl) {
        if (rawUrl == null) return null;
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception e) {
            return rawUrl;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return rawUrl;
        }
        String host = uri.getHost();
        if (host == null) return rawUrl;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("x.com") || host.equals("twitter.com") || host.equals("t.co")) {
            return rawUrl;
        }

        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String rawQuery = uri.getRawQuery();

        if (AMAZON_HOST.matcher(host).find()) {
            Matcher m = ASIN_IN_PATH.matcher(path);
            if (!m.find()) {
                m = ASIN_OBIDOS.matcher(path);
                if (!m.find()) m = null;
            }
            if (m != null) {
                String asin = m.group(1).toUpperCase(Locale.ROOT);
                String hostOut = host;
                if (hostOut.startsWith("amazon.")) hostOut = "www." + hostOut;
                return "https://" + hostOut + "/dp/" + asin;
            }
            // No ASIN: drop Amazon affiliate/click params + generic ones.
            String kept = filterQuery(rawQuery, true);
            return rebuild(uri, kept);
        }

        String kept = filterQuery(rawQuery, false);
        return rebuild(uri, kept);
    }

    private static String rebuild(URI uri, String keptQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
        if (uri.getRawPath() != null) sb.append(uri.getRawPath());
        if (keptQuery != null && !keptQuery.isEmpty()) {
            sb.append('?').append(keptQuery);
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(uri.getRawFragment());
        }
        return sb.toString();
    }

    /** Returns the query string with droppable params removed (order kept). */
    private static String filterQuery(String rawQuery, boolean amazon) {
        if (rawQuery == null || rawQuery.isEmpty()) return rawQuery;
        List<String> kept = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            String rawName = part;
            int eq = part.indexOf('=');
            if (eq >= 0) rawName = part.substring(0, eq);
            String name = decode(rawName);
            String value = eq >= 0 ? decode(part.substring(eq + 1)) : "";
            if (!isDroppable(name, value, amazon)) {
                kept.add(part);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) sb.append('&');
            sb.append(kept.get(i));
        }
        return sb.toString();
    }

    private static boolean isDroppable(String name, String value, boolean amazon) {
        String n = name.toLowerCase(Locale.ROOT);
        if (PRODUCT_PARAMS.contains(n)) return false;
        if (amazon) {
            if (n.equals("tag") || n.equals("th") || n.equals("psc")) return true;
            if (n.startsWith("pd_rd_") || n.startsWith("pf_rd_")) return true;
        }
        if (n.startsWith("utm_")) return true;
        if (AFFILIATE_PARAMS.contains(n)) return true;
        if (TRACKING_PARAMS.contains(n)) return true;
        if (n.equals("scid") && SCID_AFFILIATE.matcher(value).find()) return true;
        return false;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        } catch (Exception e) {
            return s;
        }
    }

    // ------------------------------------------------------------------
    // Short-link resolution
    // ------------------------------------------------------------------

    static String resolveIfShort(String rawUrl) {
        String host;
        try {
            host = new URI(rawUrl).getHost();
        } catch (Exception e) {
            return rawUrl;
        }
        if (host == null) return rawUrl;
        if (!SHORT_HOSTS.contains(host.toLowerCase(Locale.ROOT))) return rawUrl;

        String cached = RESOLVE_CACHE.get(rawUrl);
        if (cached != null) return cached;

        String resolved = followRedirects(rawUrl);
        // Cache failures too (as the original URL): these URLs are
        // immutable, so one resolution attempt per process is enough
        // and a dead short link cannot stall every timeline re-parse.
        RESOLVE_CACHE.put(rawUrl, resolved);
        return resolved;
    }

    private static String followRedirects(String start) {
        String current = start;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303
                        || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) return current;
                    current = new URL(new URL(current), location).toString();
                    continue;
                }
                return current;
            } catch (Exception e) {
                return current.equals(start) ? start : current;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return current;
    }
}
