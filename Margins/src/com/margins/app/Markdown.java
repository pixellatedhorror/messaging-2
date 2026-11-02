package com.margins.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain-text export for notes apps (Obsidian, Notion, Keep, Bear…): grouped by source, oldest highlight first. */
final class Markdown {
    private Markdown() {}

    static String all(JSONObject db) {
        List<JSONObject> hs = Store.highlights(db);
        int sources = 0;
        Map<String, List<JSONObject>> groups = group(db, hs);
        for (String k : groups.keySet()) if (!k.isEmpty()) sources++;
        StringBuilder b = new StringBuilder();
        b.append("# Margins\n\n");
        b.append("Exported ").append(Store.today()).append(" · ").append(hs.size()).append(hs.size() == 1 ? " highlight" : " highlights")
         .append(" from ").append(sources).append(sources == 1 ? " source" : " sources").append("\n");
        JSONArray src = db.optJSONArray("sources");
        for (Map.Entry<String, List<JSONObject>> e : groups.entrySet()) {
            b.append("\n---\n\n");
            section(b, Store.find(src, e.getKey()), e.getValue(), "##");
        }
        return b.toString();
    }

    /** One source (or "" for Unsorted), for "Share as text". */
    static String source(JSONObject db, String sourceId) {
        List<JSONObject> hs = new ArrayList<>();
        for (JSONObject h : Store.highlights(db)) if (h.optString("sourceId").equals(sourceId == null ? "" : sourceId)) hs.add(h);
        sortByDate(hs);
        StringBuilder b = new StringBuilder();
        section(b, Store.find(db.optJSONArray("sources"), sourceId), hs, "#");
        return b.toString().trim() + "\n";
    }

    /** Sources in order of most recent highlight; Unsorted last. */
    private static Map<String, List<JSONObject>> group(JSONObject db, List<JSONObject> hs) {
        final Map<String, String> used = Store.lastUsed(db);
        Map<String, List<JSONObject>> m = new LinkedHashMap<>();
        for (JSONObject h : hs) {
            String k = h.optString("sourceId");
            if (!k.isEmpty() && Store.find(db.optJSONArray("sources"), k) == null) k = "";
            if (!m.containsKey(k)) m.put(k, new ArrayList<JSONObject>());
            m.get(k).add(h);
        }
        List<String> keys = new ArrayList<>(m.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                if (a.isEmpty() != b.isEmpty()) return a.isEmpty() ? 1 : -1;
                String x = used.get(a), y = used.get(b);
                return (y == null ? "" : y).compareTo(x == null ? "" : x);
            }
        });
        Map<String, List<JSONObject>> out = new LinkedHashMap<>();
        for (String k : keys) { sortByDate(m.get(k)); out.put(k, m.get(k)); }
        return out;
    }

    private static void sortByDate(List<JSONObject> l) {
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return a.optString("created").compareTo(b.optString("created")); }
        });
    }

    private static void section(StringBuilder b, JSONObject s, List<JSONObject> hs, String h) {
        b.append(h).append(' ').append(oneLine(Store.title(s))).append("\n\n");
        if (s != null) {
            List<String> meta = new ArrayList<>();
            if (!s.optString("author").isEmpty()) meta.add("*" + oneLine(s.optString("author")) + "*");
            if (!s.optString("url").isEmpty()) meta.add("[" + Urls.host(s.optString("url")) + "](" + s.optString("url") + ")");
            if (!meta.isEmpty()) { b.append(join(meta, " · ")).append("\n\n"); }
        }
        for (JSONObject x : hs) {
            for (String line : x.optString("text").split("\n", -1)) b.append("> ").append(line).append('\n');
            b.append('\n');
            if (!x.optString("note").isEmpty()) b.append(x.optString("note").trim()).append("\n\n");
            List<String> foot = new ArrayList<>();
            JSONArray tags = x.optJSONArray("tags");
            if (tags != null && tags.length() > 0) {
                StringBuilder t = new StringBuilder();
                for (int i = 0; i < tags.length(); i++) { if (i > 0) t.append(' '); t.append('#').append(tags.optString(i).replace(' ', '-')); }
                foot.add(t.toString());
            }
            if (!x.optString("location").isEmpty()) foot.add(x.optString("location"));
            foot.add(x.optString("created").length() >= 10 ? x.optString("created").substring(0, 10) : x.optString("created"));
            if (x.optString("url").contains("#:~:text=")) foot.add("[open passage](" + x.optString("url") + ")");
            b.append(join(foot, " · ")).append("\n\n");
        }
    }

    private static String oneLine(String s) { return s.replaceAll("\\s+", " ").trim(); }

    private static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(sep); b.append(s); }
        return b.toString();
    }
}
