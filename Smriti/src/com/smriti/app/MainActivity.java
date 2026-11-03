package com.smriti.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A WebView shell. The whole app (data model + UI) lives in assets/index.html; Java only
 * saves the JSON file, talks to Open Library, caches covers and handles backup files.
 */
public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    /** Covers are requested as https://smriti.local/cover/<coverId>-<S|M|L>.jpg and served from the cache. */
    static final String COVER_HOST = "smriti.local";
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#3B3561"));
        w.setNavigationBarColor(Color.parseColor("#F7F3EC"));
        if (android.os.Build.VERSION.SDK_INT >= 26)
            w.getDecorView().setSystemUiVisibility(0x00000010 /* SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR */);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setBackgroundColor(Color.parseColor("#F7F3EC"));
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            // (WebView, WebResourceRequest) overload is API 24 (= minSdk); compiled against API 23, so no @Override.
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                openExternal(r.getUrl().toString());
                return true;
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                openExternal(url);
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!COVER_HOST.equals(u.getHost())) return null;
                File f = Net.cover(MainActivity.this, u.getLastPathSegment());
                try {
                    if (f != null) return new WebResourceResponse("image/jpeg", null, new FileInputStream(f));
                } catch (Exception ignored) { }
                return new WebResourceResponse("image/jpeg", null, 404, "Not found",
                        null, new java.io.ByteArrayInputStream(new byte[0]));
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
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

    private void js(final String code) {
        main.post(new Runnable() { @Override public void run() { if (web != null) web.evaluateJavascript(code, null); } });
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

    // ------------------------------------------------------------------ backup / restore

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            new Thread(new Runnable() { @Override public void run() { doExport(uri); } }).start();
        } else if (requestCode == REQ_IMPORT) {
            new Thread(new Runnable() { @Override public void run() { doImport(uri); } }).start();
        }
    }

    private void doExport(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new Exception("Can't write file");
            os.write(Store.load(this).getBytes("UTF-8"));
            os.close();
            toast("Backup saved");
        } catch (Exception e) {
            toast("Backup failed: " + e.getMessage());
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
            if (!text.startsWith("{")) throw new Exception("Choose a Smriti backup (.json)");
            new JSONObject(text); // validate
            js("window.onRestore && window.onRestore(" + JSONObject.quote(text) + ")");
        } catch (Exception e) {
            toast("Restore failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ JS bridge

    final class Bridge {
        @JavascriptInterface public String load() { return Store.load(MainActivity.this); }

        @JavascriptInterface public boolean save(String json) {
            try { Store.save(MainActivity.this, json); return true; }
            catch (Exception e) { toast("Save failed: " + e.getMessage()); return false; }
        }

        /** GET an Open Library URL off the main thread; answers with window.onNet(id, status, body). */
        @JavascriptInterface public void get(final String url, final String id) {
            new Thread(new Runnable() { @Override public void run() {
                String[] r = Net.get(url);
                js("window.onNet && window.onNet(" + JSONObject.quote(id) + "," + r[0] + "," + JSONObject.quote(r[1]) + ")");
            }}).start();
        }

        @JavascriptInterface public void open(String url) { if (url != null && !url.isEmpty()) openExternal(url); }

        @JavascriptInterface public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            try { startActivity(Intent.createChooser(i, "Share")); } catch (ActivityNotFoundException e) { toast("Nothing to share with"); }
        }

        @JavascriptInterface public void toast(String msg) { MainActivity.this.toast(msg); }

        @JavascriptInterface public void backup() {
            main.post(new Runnable() { @Override public void run() {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "Smriti-backup-" + Store.today() + ".json");
                try { startActivityForResult(i, REQ_EXPORT); }
                catch (ActivityNotFoundException e) { MainActivity.this.toast("No file picker available"); }
            }});
        }

        @JavascriptInterface public void restore() {
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
