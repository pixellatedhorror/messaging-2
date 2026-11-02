package com.margins.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    static final String EXTRA_HIGHLIGHT = "com.margins.app.HIGHLIGHT";
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String pendingExport;       // "xlsx" | "md" | "json"
    private volatile String pendingOpen = ""; // highlight to show (from the widget)
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#2F4858"));
        w.setNavigationBarColor(Color.parseColor("#F6F3EE"));
        if (android.os.Build.VERSION.SDK_INT >= 26)
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setBackgroundColor(Color.parseColor("#F6F3EE"));
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                openExternal(r.getUrl().toString());
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) { pageReady = true; }
        });
        handleIntent(getIntent());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        Store.setListener(new Store.Listener() {
            @Override public void onChanged() { refreshUi(); }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        final String id = pendingOpen;
        if (pageReady && !id.isEmpty()) {
            pendingOpen = "";
            web.evaluateJavascript("window.openHighlight && window.openHighlight(" + JSONObject.quote(id) + ")", null);
        }
    }

    private void handleIntent(Intent intent) {
        String id = intent == null ? null : intent.getStringExtra(EXTRA_HIGHLIGHT);
        if (id != null) pendingOpen = id;
    }

    @Override protected void onResume() { super.onResume(); refreshUi(); }

    @Override protected void onDestroy() {
        Store.setListener(null);
        super.onDestroy();
    }

    private void refreshUi() {
        main.post(new Runnable() {
            @Override public void run() {
                if (web != null) web.evaluateJavascript("window.onDataChanged && window.onDataChanged()", null);
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (!"true".equals(v)) MainActivity.super.onBackPressed();
            }
        });
    }

    private void toast(final String msg) {
        main.post(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); } });
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("No app can open this link");
        }
    }

    // ------------------------------------------------------------------ export / import

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            final String fmt = pendingExport;
            new Thread(new Runnable() { @Override public void run() { doExport(uri, fmt); } }).start();
        } else if (requestCode == REQ_IMPORT) {
            new Thread(new Runnable() { @Override public void run() { doImport(uri); } }).start();
        }
    }

    private void doExport(Uri uri, String fmt) {
        try {
            JSONObject db = Store.load(this);
            OutputStream os = getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new Exception("Can't write file");
            if ("xlsx".equals(fmt)) Xlsx.write(db, os);
            else if ("md".equals(fmt)) os.write(Markdown.all(db).getBytes("UTF-8"));
            else os.write(db.toString(1).getBytes("UTF-8"));
            os.close();
            int n = db.getJSONArray("highlights").length();
            toast("Exported " + n + (n == 1 ? " highlight" : " highlights"));
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    private void doImport(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("Can't open file");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            String text = new String(b.toByteArray(), "UTF-8").trim();
            if (text.startsWith("﻿")) text = text.substring(1);
            if (!text.startsWith("{")) throw new Exception("Choose a Margins backup (.json)");
            int[] r = Store.merge(this, new JSONObject(text));
            toast("Restored " + r[0] + (r[0] == 1 ? " highlight" : " highlights") + " and " + r[1] + (r[1] == 1 ? " source" : " sources"));
        } catch (Exception e) {
            toast("Restore failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ JS bridge

    final class Bridge {
        @JavascriptInterface public String data() { return Store.load(MainActivity.this).toString(); }

        @JavascriptInterface public String putHighlight(String json) {
            try { return Store.putHighlight(MainActivity.this, new JSONObject(json)).toString(); }
            catch (Exception e) { toast("Save failed: " + e.getMessage()); return ""; }
        }

        @JavascriptInterface public void deleteHighlight(String id) {
            try { Store.deleteHighlight(MainActivity.this, id); } catch (Exception e) { toast("Delete failed: " + e.getMessage()); }
        }

        @JavascriptInterface public String putSource(String json) {
            try { return Store.putSource(MainActivity.this, new JSONObject(json)).toString(); }
            catch (Exception e) { toast("Save failed: " + e.getMessage()); return ""; }
        }

        @JavascriptInterface public void deleteSource(String id) {
            try { Store.deleteSource(MainActivity.this, id); } catch (Exception e) { toast("Delete failed: " + e.getMessage()); }
        }

        @JavascriptInterface public void mergeSource(String from, String into) {
            try { Store.mergeSource(MainActivity.this, from, into); } catch (Exception e) { toast("Merge failed: " + e.getMessage()); }
        }

        @JavascriptInterface public void renameTag(String from, String to) {
            try { Store.renameTag(MainActivity.this, from, to); } catch (Exception e) { toast("Failed: " + e.getMessage()); }
        }

        @JavascriptInterface public String markdown(String sourceId) {
            return Markdown.source(Store.load(MainActivity.this), sourceId);
        }

        @JavascriptInterface public void open(String url) { if (url != null && !url.isEmpty()) openExternal(url); }

        @JavascriptInterface public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            try { startActivity(Intent.createChooser(i, "Share")); } catch (ActivityNotFoundException e) { toast("Nothing to share with"); }
        }

        @JavascriptInterface public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Highlight", text));
            if (android.os.Build.VERSION.SDK_INT < 33) toast("Copied");
        }

        @JavascriptInterface public void toast(String msg) { MainActivity.this.toast(msg); }

        @JavascriptInterface public String pendingOpen() { String p = pendingOpen; pendingOpen = ""; return p; }

        @JavascriptInterface public void exportAs(final String fmt) {
            main.post(new Runnable() { @Override public void run() {
                pendingExport = fmt;
                String ext = fmt.equals("xlsx") ? "xlsx" : fmt.equals("md") ? "md" : "json";
                String mime = fmt.equals("xlsx") ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : fmt.equals("md") ? "text/markdown" : "application/json";
                String name = (fmt.equals("json") ? "Margins-backup-" : "Margins-") + Store.today() + "." + ext;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_EXPORT); }
                catch (ActivityNotFoundException e) { MainActivity.this.toast("No file picker available"); }
            }});
        }

        @JavascriptInterface public void importFile() {
            main.post(new Runnable() { @Override public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                try { startActivityForResult(i, REQ_IMPORT); }
                catch (ActivityNotFoundException e) { MainActivity.this.toast("No file picker available"); }
            }});
        }
    }
}
