package com.margins.app;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Downloads an article page directly from the phone and pulls out title / author / publication. */
final class Metadata {
    String title = "", author = "", site = "", finalUrl = "";

    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/126.0 Mobile Safari/537.36";

    static Metadata fetch(String startUrl) throws Exception {
        String url = startUrl;
        HttpURLConnection c = null;
        for (int hop = 0; hop < 8; hop++) {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            break;
        }
        InputStream in;
        try { in = c.getInputStream(); } catch (Exception e) { in = c.getErrorStream(); }
        if (in == null) throw new Exception("No response");
        if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < MAX_BYTES) {
            bos.write(buf, 0, n);
            // Everything we need is in <head> (plus JSON-LD); stop early on very large pages.
            if (bos.size() > 800_000 && (bos.size() / 16384) % 16 == 0
                    && bos.toString("ISO-8859-1").contains("</head>")) break;
        }
        in.close();
        c.disconnect();
        byte[] body = bos.toByteArray();

        String charset = null;
        String ct = c.getContentType();
        if (ct != null) {
            Matcher m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(ct);
            if (m.find()) charset = m.group(1);
        }
        if (charset == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), "ISO-8859-1");
            Matcher m = Pattern.compile("<meta[^>]+charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
            if (m.find()) charset = m.group(1);
        }
        String html;
        try { html = new String(body, Charset.forName(charset == null ? "UTF-8" : charset)); }
        catch (Exception e) { html = new String(body, "UTF-8"); }

        Metadata md = parse(html, url);
        md.finalUrl = url;
        return md;
    }

    // ---------------------------------------------------------------- parsing

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>\"']+))");
    private static final Pattern LDJSON = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']?application/ld\\+json[\"']?[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static Metadata parse(String html, String url) {
        Metadata md = new Metadata();
        Map<String, List<String>> meta = new HashMap<>();
        Matcher m = META.matcher(html);
        while (m.find()) {
            Map<String, String> a = attrs(m.group());
            String key = first(a.get("property"), a.get("name"), a.get("itemprop"));
            String val = a.get("content");
            if (key == null || val == null) continue;
            key = key.toLowerCase();
            List<String> l = meta.get(key);
            if (l == null) { l = new ArrayList<>(); meta.put(key, l); }
            l.add(clean(val));
        }

        String ldHeadline = "", ldAuthor = "", ldPublisher = "";
        Matcher lm = LDJSON.matcher(html);
        while (lm.find()) {
            try {
                Object root = new JSONTokener(lm.group(1).trim()).nextValue();
                List<JSONObject> objs = new ArrayList<>();
                collect(root, objs, 0);
                for (JSONObject o : objs) {
                    if (!isArticle(o)) continue;
                    if (ldHeadline.isEmpty()) ldHeadline = clean(first(o.optString("headline", null), o.optString("name", null), ""));
                    if (ldAuthor.isEmpty()) ldAuthor = names(o.opt("author"));
                    if (ldAuthor.isEmpty()) ldAuthor = names(o.opt("creator"));
                    if (ldPublisher.isEmpty()) ldPublisher = names(o.opt("publisher"));
                }
            } catch (Exception ignored) { }
        }

        String site = first(get(meta, "og:site_name"), ldPublisher.isEmpty() ? null : ldPublisher,
                get(meta, "application-name"));
        md.site = site == null ? Urls.host(url) : site;

        String docTitle = null;
        Matcher tm = TITLE.matcher(html);
        if (tm.find()) docTitle = clean(tm.group(1));
        String title = first(get(meta, "og:title"), get(meta, "twitter:title"),
                ldHeadline.isEmpty() ? null : ldHeadline, get(meta, "title"), get(meta, "citation_title"), docTitle);
        md.title = stripSiteSuffix(title == null ? "" : title, md.site);

        String author = ldAuthor.isEmpty() ? null : ldAuthor;
        if (author == null) author = joinAll(meta.get("citation_author"));
        if (author == null) author = notUrl(get(meta, "author"));
        if (author == null) author = notUrl(get(meta, "article:author"));
        if (author == null) author = notUrl(get(meta, "parsely-author"));
        if (author == null) author = notUrl(get(meta, "sailthru.author"));
        if (author == null) author = notUrl(get(meta, "byl"));
        if (author == null) author = notUrl(get(meta, "dc.creator"));
        if (author == null) author = notUrl(get(meta, "dcterms.creator"));
        if (author == null) author = byline(html);
        md.author = tidyAuthor(author == null ? "" : author);
        if (md.author.equalsIgnoreCase(md.site)) md.author = "";
        return md;
    }

    private static Map<String, String> attrs(String tag) {
        Map<String, String> a = new HashMap<>();
        Matcher m = ATTR.matcher(tag);
        while (m.find()) {
            String v = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
            a.put(m.group(1).toLowerCase(), v);
        }
        return a;
    }

    private static void collect(Object node, List<JSONObject> out, int depth) {
        if (depth > 6 || node == null) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), out, depth + 1);
        } else if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            out.add(o);
            collect(o.opt("@graph"), out, depth + 1);
            collect(o.opt("mainEntity"), out, depth + 1);
        }
    }

    private static boolean isArticle(JSONObject o) {
        String t = String.valueOf(o.opt("@type")).toLowerCase();
        return t.contains("article") || t.contains("blogposting") || t.contains("report")
                || t.contains("posting") || t.contains("scholarly") || t.contains("creativework")
                || (o.has("headline") && o.has("author"));
    }

    /** Turn a schema.org author value (string / Person / list of Persons) into "A, B". */
    private static String names(Object v) {
        if (v == null) return "";
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            for (int i = 0; i < a.length(); i++) { String s = names(a.opt(i)); if (!s.isEmpty()) out.add(s); }
        } else if (v instanceof JSONObject) {
            String s = ((JSONObject) v).optString("name", "");
            if (!s.isEmpty()) out.add(clean(s));
        } else if (v instanceof String) {
            String s = clean((String) v);
            if (!s.isEmpty() && !s.startsWith("http")) out.add(s);
        }
        return android.text.TextUtils.join(", ", out);
    }

    private static final Pattern BYLINE = Pattern.compile(
            "<[^>]+(?:rel=[\"']author[\"']|class=[\"'][^\"']*(?:byline|author)[^\"']*[\"'])[^>]*>(.{2,200}?)</",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static String byline(String html) {
        Matcher m = BYLINE.matcher(html);
        while (m.find()) {
            String s = clean(m.group(1));
            if (s.length() >= 3 && s.length() <= 80 && !s.contains("{") && Character.isLetter(s.charAt(0))) return s;
        }
        return null;
    }

    private static String tidyAuthor(String a) {
        a = a.replaceFirst("(?i)^\\s*(written\\s+)?by[:\\s]+", "").trim();
        a = a.replaceAll("\\s+", " ");
        if (a.startsWith("@")) return a;
        if (a.length() > 120) a = a.substring(0, 120).trim() + "…";
        return a;
    }

    private static String stripSiteSuffix(String t, String site) {
        if (site == null || site.isEmpty()) return t;
        String[] seps = {" | ", " - ", " — ", " – ", " :: ", " · "};
        for (String sep : seps) {
            int i = t.lastIndexOf(sep);
            if (i > 0) {
                String tail = t.substring(i + sep.length()).trim();
                if (tail.equalsIgnoreCase(site) || site.toLowerCase().contains(tail.toLowerCase()) && tail.length() > 2)
                    return t.substring(0, i).trim();
            }
        }
        return t;
    }

    private static String get(Map<String, List<String>> meta, String key) {
        List<String> l = meta.get(key);
        if (l == null) return null;
        for (String s : l) if (!s.isEmpty()) return s;
        return null;
    }

    private static String joinAll(List<String> l) {
        if (l == null || l.isEmpty()) return null;
        LinkedHashSet<String> set = new LinkedHashSet<>(l);
        set.remove("");
        return set.isEmpty() ? null : android.text.TextUtils.join(", ", set);
    }

    private static String notUrl(String s) {
        if (s == null || s.isEmpty() || s.startsWith("http") || s.startsWith("//")) return null;
        return s;
    }

    private static String first(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return null;
    }

    @SuppressWarnings("deprecation")
    static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?s)<[^>]*>", " ");
        if (t.indexOf('&') >= 0) t = Html.fromHtml(t).toString();
        return t.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
