package com.margins.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * All data lives in one JSON file in the app's private storage:
 * { "sources": [ {id,title,author,url,kind,created} ], "highlights": [ {id,text,note,tags,sourceId,created,app,url,location} ] }
 * A highlight with an empty sourceId is "Unsorted". Nothing leaves the device.
 */
public final class Store {
    private static final String FILE = "margins.json";
    private static final Object LOCK = new Object();
    private static final Random RNG = new Random();

    public interface Listener { void onChanged(); }
    private static volatile Listener listener;
    public static void setListener(Listener l) { listener = l; }

    private Store() {}

    public static String nowMinute() { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()); }
    public static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RNG.nextInt(1 << 20), 36);
    }

    // ------------------------------------------------------------------ file

    public static JSONObject load(Context ctx) {
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), FILE);
            JSONObject db = null;
            if (f.exists()) {
                try {
                    FileInputStream in = new FileInputStream(f);
                    byte[] buf = new byte[(int) f.length()];
                    int off = 0, n;
                    while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                    in.close();
                    db = new JSONObject(new String(buf, 0, off, "UTF-8"));
                } catch (Exception e) {
                    // Keep the unreadable file rather than silently overwriting it later.
                    f.renameTo(new File(ctx.getFilesDir(), FILE + ".corrupt-" + System.currentTimeMillis()));
                }
            }
            return normalizeDb(db == null ? new JSONObject() : db);
        }
    }

    public static void save(Context ctx, JSONObject db) throws IOException {
        synchronized (LOCK) {
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(db.toString().getBytes("UTF-8"));
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(new File(dir, FILE))) throw new IOException("Could not save data");
        }
        Listener l = listener;
        if (l != null) l.onChanged();
        DailyWidget.refresh(ctx);
    }

    static JSONObject normalizeDb(JSONObject db) {
        try {
            if (db.optJSONArray("sources") == null) db.put("sources", new JSONArray());
            if (db.optJSONArray("highlights") == null) db.put("highlights", new JSONArray());
            db.put("version", 1);
        } catch (JSONException ignored) {}
        return db;
    }

    static JSONObject normalizeHighlight(JSONObject h) throws JSONException {
        if (h.optString("id").isEmpty()) h.put("id", newId());
        for (String k : new String[]{"text", "note", "sourceId", "app", "url", "location"})
            if (!h.has(k) || h.isNull(k)) h.put(k, "");
        if (h.optString("created").isEmpty()) h.put("created", nowMinute());
        h.put("tags", cleanTags(h.optJSONArray("tags")));
        return h;
    }

    static JSONObject normalizeSource(JSONObject s) throws JSONException {
        if (s.optString("id").isEmpty()) s.put("id", newId());
        for (String k : new String[]{"title", "author", "url"})
            if (!s.has(k) || s.isNull(k)) s.put(k, "");
        String k = s.optString("kind");
        if (!k.equals("web") && !k.equals("book") && !k.equals("other")) s.put("kind", s.optString("url").isEmpty() ? "other" : "web");
        if (s.optString("created").isEmpty()) s.put("created", nowMinute());
        return s;
    }

    /** Trim, drop a leading '#', lower-case, de-duplicate. */
    static JSONArray cleanTags(JSONArray in) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (in != null) for (int i = 0; i < in.length(); i++) {
            String t = tag(in.optString(i));
            if (!t.isEmpty()) set.add(t);
        }
        return new JSONArray(new ArrayList<>(set));
    }

    static String tag(String t) {
        if (t == null) return "";
        t = t.trim().replaceFirst("^#+", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    // ------------------------------------------------------------------ lookups

    static int indexOf(JSONArray arr, String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return i;
        }
        return -1;
    }

    static JSONObject find(JSONArray arr, String id) {
        int i = indexOf(arr, id);
        return i < 0 ? null : arr.optJSONObject(i);
    }

    /** Letters and digits only, lower case: "The Waves " == "the waves". */
    static String titleKey(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    /** Existing source matching a capture: same page for web, same title (and author if both known) for books. */
    static JSONObject matchSource(JSONArray sources, Capture.Result c) {
        String key = c.sourceUrl.isEmpty() ? "" : Urls.key(c.sourceUrl);
        String tk = titleKey(c.title), ak = titleKey(c.author);
        for (int i = 0; i < sources.length(); i++) {
            JSONObject s = sources.optJSONObject(i);
            if (s == null) continue;
            if (!key.isEmpty() && key.equals(Urls.key(s.optString("url")))) return s;
            if (key.isEmpty() && !tk.isEmpty() && tk.equals(titleKey(s.optString("title")))) {
                String sa = titleKey(s.optString("author"));
                if (ak.isEmpty() || sa.isEmpty() || ak.equals(sa)) return s;
            }
        }
        return null;
    }

    /** Last time each source got a highlight (yyyy-MM-dd HH:mm), falling back to when it was created. */
    static Map<String, String> lastUsed(JSONObject db) {
        Map<String, String> m = new HashMap<>();
        JSONArray src = db.optJSONArray("sources");
        for (int i = 0; i < src.length(); i++) {
            JSONObject s = src.optJSONObject(i);
            if (s != null) m.put(s.optString("id"), s.optString("created"));
        }
        JSONArray hs = db.optJSONArray("highlights");
        for (int i = 0; i < hs.length(); i++) {
            JSONObject h = hs.optJSONObject(i);
            if (h == null) continue;
            String sid = h.optString("sourceId"), d = h.optString("created");
            String cur = m.get(sid);
            if (cur != null && d.compareTo(cur) > 0) m.put(sid, d);
        }
        return m;
    }

    /** Sources ordered by most recently used. */
    static List<JSONObject> recentSources(JSONObject db) {
        final Map<String, String> used = lastUsed(db);
        List<JSONObject> l = new ArrayList<>();
        JSONArray src = db.optJSONArray("sources");
        for (int i = 0; i < src.length(); i++) if (src.optJSONObject(i) != null) l.add(src.optJSONObject(i));
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                String x = used.get(a.optString("id")), y = used.get(b.optString("id"));
                return (y == null ? "" : y).compareTo(x == null ? "" : x);
            }
        });
        return l;
    }

    /** Most recent highlight saved from the given app, or null. */
    static JSONObject lastFromApp(JSONObject db, String app) {
        if (app == null || app.isEmpty()) return null;
        JSONArray hs = db.optJSONArray("highlights");
        JSONObject best = null;
        for (int i = 0; i < hs.length(); i++) {
            JSONObject h = hs.optJSONObject(i);
            if (h != null && app.equals(h.optString("app"))
                    && (best == null || h.optString("created").compareTo(best.optString("created")) >= 0)) best = h;
        }
        return best;
    }

    /** Minutes between two "yyyy-MM-dd HH:mm" stamps (b - a), or Long.MAX_VALUE if unparseable. */
    static long minutesBetween(String a, String b) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            return (f.parse(b).getTime() - f.parse(a).getTime()) / 60000L;
        } catch (Exception e) { return Long.MAX_VALUE; }
    }

    // ------------------------------------------------------------------ capture

    /** What {@link #capture} did, for the pop-up card. */
    static final class Captured {
        JSONObject highlight, source;
        boolean newSource, duplicate, guessed;
    }

    /** How long after your last highlight from an app a text-only highlight is assumed to come from the same source. */
    static final long SAME_SOURCE_MINUTES = 180;

    /** Save a shared passage straight away, attaching (or creating) its source. */
    static Captured capture(Context ctx, Capture.Result c, String app) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray sources = db.getJSONArray("sources"), hs = db.getJSONArray("highlights");
            Captured out = new Captured();

            JSONObject src = null;
            if (c.hasSource()) {
                src = matchSource(sources, c);
                if (src == null) {
                    src = new JSONObject();
                    src.put("title", c.title);
                    src.put("author", c.author);
                    src.put("url", c.sourceUrl);
                    src.put("kind", c.kind);
                    normalizeSource(src);
                    sources.put(src);
                    out.newSource = true;
                } else {
                    // Fill in anything this share knows that the stored source doesn't.
                    if (src.optString("author").isEmpty() && !c.author.isEmpty()) src.put("author", c.author);
                    if (src.optString("url").isEmpty() && !c.sourceUrl.isEmpty()) src.put("url", c.sourceUrl);
                    String t = src.optString("title");
                    if ((t.isEmpty() || t.equals(Urls.host(src.optString("url")))) && !c.title.isEmpty()) src.put("title", c.title);
                }
            } else {
                // Only text came through (PDF readers, many apps). If you highlighted from the same app recently,
                // it's almost certainly the same book or document.
                JSONObject last = lastFromApp(db, app);
                if (last != null && !last.optString("sourceId").isEmpty()
                        && minutesBetween(last.optString("created"), nowMinute()) <= SAME_SOURCE_MINUTES) {
                    src = find(sources, last.optString("sourceId"));
                    out.guessed = src != null;
                }
            }
            String sid = src == null ? "" : src.optString("id");

            // Sharing the same passage twice shouldn't make two copies.
            for (int i = 0; i < hs.length(); i++) {
                JSONObject h = hs.optJSONObject(i);
                if (h != null && h.optString("text").equals(c.quote) && h.optString("sourceId").equals(sid)) {
                    out.highlight = h; out.source = src; out.duplicate = true; out.newSource = false;
                    return out;
                }
            }

            JSONObject h = new JSONObject();
            h.put("text", c.quote);
            h.put("sourceId", sid);
            h.put("app", app == null ? "" : app);
            h.put("url", c.passageUrl);
            h.put("location", c.location);
            normalizeHighlight(h);
            hs.put(h);
            save(ctx, db);
            out.highlight = h; out.source = src;
            return out;
        }
    }

    // ------------------------------------------------------------------ edits

    /** Insert or update a highlight's editable fields. Returns the stored highlight. */
    static JSONObject putHighlight(Context ctx, JSONObject in) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray hs = db.getJSONArray("highlights");
            JSONObject h = find(hs, in.optString("id"));
            if (h == null) { h = new JSONObject(); if (!in.optString("id").isEmpty()) h.put("id", in.optString("id")); hs.put(h); }
            for (String k : new String[]{"text", "note", "sourceId", "location", "url"})
                if (in.has(k)) h.put(k, in.optString(k).trim());
            if (in.has("tags")) h.put("tags", in.optJSONArray("tags"));
            if (in.has("created") && in.optString("created").matches("\\d{4}-\\d{2}-\\d{2}.*")) h.put("created", in.optString("created"));
            normalizeHighlight(h);
            if (find(db.getJSONArray("sources"), h.optString("sourceId")) == null) h.put("sourceId", "");
            save(ctx, db);
            return h;
        }
    }

    static void deleteHighlight(Context ctx, String id) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray hs = db.getJSONArray("highlights");
            int i = indexOf(hs, id);
            if (i < 0) return;
            hs.remove(i);
            save(ctx, db);
        }
    }

    /** Remove a highlight that was just captured, and its source too if the capture created it and nothing else uses it. */
    static void undoCapture(Context ctx, String highlightId, String newSourceId) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray hs = db.getJSONArray("highlights");
            int i = indexOf(hs, highlightId);
            if (i >= 0) hs.remove(i);
            if (newSourceId != null && !newSourceId.isEmpty() && count(db, newSourceId) == 0) {
                int s = indexOf(db.getJSONArray("sources"), newSourceId);
                if (s >= 0) db.getJSONArray("sources").remove(s);
            }
            save(ctx, db);
        }
    }

    static int count(JSONObject db, String sourceId) {
        JSONArray hs = db.optJSONArray("highlights");
        int n = 0;
        for (int i = 0; i < hs.length(); i++) {
            JSONObject h = hs.optJSONObject(i);
            if (h != null && sourceId.equals(h.optString("sourceId"))) n++;
        }
        return n;
    }

    /** Insert or update a source. Returns the stored source. */
    static JSONObject putSource(Context ctx, JSONObject in) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray src = db.getJSONArray("sources");
            JSONObject s = find(src, in.optString("id"));
            if (s == null) { s = new JSONObject(); src.put(s); }
            for (String k : new String[]{"title", "author", "url", "kind"})
                if (in.has(k)) s.put(k, in.optString(k).trim());
            normalizeSource(s);
            save(ctx, db);
            return s;
        }
    }

    /** Fill in title/author found by downloading the page, without overwriting anything you typed. */
    static JSONObject fillSource(Context ctx, String id, String title, String author) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONObject s = find(db.getJSONArray("sources"), id);
            if (s == null) return null;
            boolean changed = false;
            String t = s.optString("title");
            if (title != null && !title.isEmpty() && (t.isEmpty() || t.equals(Urls.host(s.optString("url"))))) { s.put("title", title); changed = true; }
            if (author != null && !author.isEmpty() && s.optString("author").isEmpty()) { s.put("author", author); changed = true; }
            if (changed) save(ctx, db);
            return s;
        }
    }

    /** Delete a source. Its highlights stay, as Unsorted. */
    static void deleteSource(Context ctx, String id) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray src = db.getJSONArray("sources");
            int i = indexOf(src, id);
            if (i >= 0) src.remove(i);
            JSONArray hs = db.getJSONArray("highlights");
            for (int k = 0; k < hs.length(); k++) {
                JSONObject h = hs.optJSONObject(k);
                if (h != null && id.equals(h.optString("sourceId"))) h.put("sourceId", "");
            }
            save(ctx, db);
        }
    }

    /** Move every highlight from one source into another, then delete the first. */
    static void mergeSource(Context ctx, String fromId, String intoId) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            if (find(db.getJSONArray("sources"), intoId) == null || fromId.equals(intoId)) return;
            JSONArray hs = db.getJSONArray("highlights");
            for (int k = 0; k < hs.length(); k++) {
                JSONObject h = hs.optJSONObject(k);
                if (h != null && fromId.equals(h.optString("sourceId"))) h.put("sourceId", intoId);
            }
            int i = indexOf(db.getJSONArray("sources"), fromId);
            if (i >= 0) db.getJSONArray("sources").remove(i);
            save(ctx, db);
        }
    }

    /** Rename a tag everywhere (or delete it when the new name is empty). Merges into an existing tag of that name. */
    static void renameTag(Context ctx, String from, String to) throws Exception {
        synchronized (LOCK) {
            from = tag(from); to = tag(to);
            JSONObject db = load(ctx);
            JSONArray hs = db.getJSONArray("highlights");
            for (int i = 0; i < hs.length(); i++) {
                JSONObject h = hs.optJSONObject(i);
                if (h == null) continue;
                JSONArray t = h.optJSONArray("tags"), n = new JSONArray();
                if (t == null) continue;
                for (int k = 0; k < t.length(); k++) {
                    String x = t.optString(k);
                    if (x.equals(from)) { if (!to.isEmpty()) n.put(to); } else n.put(x);
                }
                h.put("tags", cleanTags(n));
            }
            save(ctx, db);
        }
    }

    /** Add everything from a backup that isn't here yet (matched by id). Returns {new highlights, new sources}. */
    static int[] merge(Context ctx, JSONObject in) throws Exception {
        synchronized (LOCK) {
            JSONObject db = load(ctx);
            JSONArray src = db.getJSONArray("sources"), hs = db.getJSONArray("highlights");
            int ns = 0, nh = 0;
            JSONArray inS = in.optJSONArray("sources"), inH = in.optJSONArray("highlights");
            if (inS == null && inH == null) throw new Exception("Not a Margins backup");
            if (inS != null) for (int i = 0; i < inS.length(); i++) {
                JSONObject s = inS.optJSONObject(i);
                if (s == null || (!s.optString("id").isEmpty() && indexOf(src, s.optString("id")) >= 0)) continue;
                src.put(normalizeSource(s)); ns++;
            }
            HashSet<String> sourceIds = new HashSet<>();
            for (int i = 0; i < src.length(); i++) sourceIds.add(src.optJSONObject(i).optString("id"));
            if (inH != null) for (int i = 0; i < inH.length(); i++) {
                JSONObject h = inH.optJSONObject(i);
                if (h == null || (!h.optString("id").isEmpty() && indexOf(hs, h.optString("id")) >= 0)) continue;
                normalizeHighlight(h);
                if (!sourceIds.contains(h.optString("sourceId"))) h.put("sourceId", "");
                hs.put(h); nh++;
            }
            save(ctx, db);
            return new int[]{nh, ns};
        }
    }

    // ------------------------------------------------------------------ helpers for exports and the widget

    /** Display title for a source: its title, else the site name, else "Untitled". */
    static String title(JSONObject s) {
        if (s == null) return "Unsorted";
        String t = s.optString("title");
        if (!t.isEmpty()) return t;
        if (!s.optString("url").isEmpty()) return Urls.host(s.optString("url"));
        return "Untitled";
    }

    static String tagsText(JSONObject h) {
        JSONArray t = h.optJSONArray("tags");
        StringBuilder b = new StringBuilder();
        if (t != null) for (int i = 0; i < t.length(); i++) { if (b.length() > 0) b.append(", "); b.append(t.optString(i)); }
        return b.toString();
    }

    static List<JSONObject> highlights(JSONObject db) {
        List<JSONObject> l = new ArrayList<>();
        JSONArray hs = db.optJSONArray("highlights");
        for (int i = 0; i < hs.length(); i++) if (hs.optJSONObject(i) != null) l.add(hs.optJSONObject(i));
        return l;
    }
}
