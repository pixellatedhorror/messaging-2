package com.margins.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Select text → Share → Margins (or the "Margins" entry in the text-selection menu).
 * The highlight is saved the moment this opens; the card on top only offers a note, tags and the source.
 */
public class CaptureActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private Store.Captured captured;
    private String app = "";
    private boolean closing;
    private volatile boolean undone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String text, subject = null;
        if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            CharSequence cs = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            text = cs == null ? "" : cs.toString();
        } else {
            CharSequence cs = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            text = cs == null ? "" : cs.toString();
            subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
        }
        app = appLabel();

        Capture.Result r = Capture.parse(text, subject);
        if (r.quote.isEmpty()) {
            Toast.makeText(this, "Nothing to save. Select some text first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            captured = Store.capture(this, r, app);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // A web page we haven't seen: look up its title and author in the background.
        final JSONObject src = captured.source;
        if (src != null && captured.newSource && "web".equals(src.optString("kind")) && !src.optString("url").isEmpty()
                && (src.optString("author").isEmpty() || src.optString("title").isEmpty())) {
            final String id = src.optString("id"), url = src.optString("url");
            final android.content.Context appCtx = getApplicationContext();
            new Thread(new Runnable() { @Override public void run() {
                try {
                    Metadata md = Metadata.fetch(url);
                    final JSONObject s = Store.fillSource(appCtx, id, md.title, md.author);
                    if (s != null) main.post(new Runnable() { @Override public void run() {
                        if (web != null) web.evaluateJavascript("window.onSource && window.onSource(" + s + ")", null);
                    }});
                } catch (Exception ignored) { /* offline or blocked: the page link is still saved */ }
            }}).start();
        }

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/capture.html");
    }

    /** Name of the app the text came from ("Chrome", "Kindle"), or "" if Android doesn't say. */
    private String appLabel() {
        String pkg = getCallingPackage();
        if (pkg == null) {
            Uri ref = getReferrer();
            if (ref != null && "android-app".equals(ref.getScheme())) pkg = ref.getHost();
        }
        if (pkg == null || pkg.equals(getPackageName())) return "";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return "";
        }
    }

    final class Bridge {
        @JavascriptInterface public String init() {
            try {
                JSONObject db = Store.load(CaptureActivity.this);
                JSONObject r = new JSONObject();
                r.put("highlight", captured.highlight);
                r.put("source", captured.source == null ? JSONObject.NULL : captured.source);
                r.put("duplicate", captured.duplicate);
                r.put("guessed", captured.guessed);
                r.put("app", app);

                // Recent sources (most recently used first) with highlight counts.
                Map<String, Integer> counts = new HashMap<>();
                Map<String, Integer> tagCounts = new HashMap<>();
                for (JSONObject h : Store.highlights(db)) {
                    String sid = h.optString("sourceId");
                    counts.put(sid, (counts.containsKey(sid) ? counts.get(sid) : 0) + 1);
                    JSONArray t = h.optJSONArray("tags");
                    if (t != null) for (int i = 0; i < t.length(); i++) {
                        String x = t.optString(i);
                        tagCounts.put(x, (tagCounts.containsKey(x) ? tagCounts.get(x) : 0) + 1);
                    }
                }
                JSONArray sources = new JSONArray();
                for (JSONObject s : Store.recentSources(db)) {
                    JSONObject x = new JSONObject(s.toString());
                    Integer n = counts.get(s.optString("id"));
                    x.put("n", n == null ? 0 : n);
                    sources.put(x);
                }
                r.put("sources", sources);
                List<Map.Entry<String, Integer>> tl = new ArrayList<>(tagCounts.entrySet());
                Collections.sort(tl, new java.util.Comparator<Map.Entry<String, Integer>>() {
                    @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        int c = b.getValue() - a.getValue();
                        return c != 0 ? c : a.getKey().compareTo(b.getKey());
                    }
                });
                JSONArray tags = new JSONArray();
                for (Map.Entry<String, Integer> e : tl) tags.put(e.getKey());
                r.put("tags", tags);
                return r.toString();
            } catch (Exception e) { return "{}"; }
        }

        /** Save edits to the highlight (text, note, tags, sourceId). Called as the card changes. */
        @JavascriptInterface public String update(String json) {
            if (undone) return "";
            try {
                JSONObject in = new JSONObject(json);
                in.put("id", captured.highlight.optString("id"));
                captured.highlight = Store.putHighlight(CaptureActivity.this, in);
                return captured.highlight.toString();
            } catch (Exception e) { toast("Couldn't save: " + e.getMessage()); return ""; }
        }

        @JavascriptInterface public String newSource(String title, String author) {
            try {
                JSONObject s = new JSONObject();
                s.put("title", title); s.put("author", author); s.put("kind", "book");
                return Store.putSource(CaptureActivity.this, s).toString();
            } catch (Exception e) { toast("Couldn't add: " + e.getMessage()); return ""; }
        }

        @JavascriptInterface public void undo() {
            undone = true;
            try {
                Store.undoCapture(CaptureActivity.this, captured.highlight.optString("id"),
                        captured.newSource && captured.source != null ? captured.source.optString("id") : "");
                toast("Removed");
            } catch (Exception e) { toast("Couldn't remove: " + e.getMessage()); }
            close();
        }

        @JavascriptInterface public void close() { CaptureActivity.this.close(); }
    }

    private void toast(final String msg) {
        main.post(new Runnable() { @Override public void run() { Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show(); } });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) { if (!"true".equals(v)) close(); }
        });
    }

    /** Leaving any other way (home button, another app on top) keeps everything typed so far. */
    @Override protected void onPause() {
        super.onPause();
        if (web != null && !closing) web.evaluateJavascript("window.flush && window.flush()", null);
    }

    private void close() {
        closing = true;
        main.post(new Runnable() { @Override public void run() {
            if (web == null) { finish(); return; }
            // Save anything still pending in the card, then go.
            web.evaluateJavascript("window.flush && window.flush()", new ValueCallback<String>() {
                @Override public void onReceiveValue(String v) { finish(); overridePendingTransition(0, 0); }
            });
        }});
    }
}
