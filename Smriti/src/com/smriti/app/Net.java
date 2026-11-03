package com.smriti.app;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** The app's only network use: Open Library lookups and cover images. */
final class Net {
    private static final String UA = "Smriti/1.0 (personal reading tracker; Android)";
    private Net() {}

    static boolean allowed(URL u) {
        String h = u.getHost();
        return "https".equals(u.getProtocol()) && ("openlibrary.org".equals(h) || "covers.openlibrary.org".equals(h));
    }

    /** Returns {status, body}. Status 0 means no connection. */
    static String[] get(String url) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            if (!allowed(u)) return new String[]{"0", "Blocked host"};
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            byte[] body = in == null ? new byte[0] : readAll(in);
            return new String[]{Integer.toString(code), new String(body, "UTF-8")};
        } catch (Exception e) {
            return new String[]{"0", String.valueOf(e.getMessage())};
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Cached cover file for a name like "8231856-M.jpg", downloading it once. Null if unavailable. */
    static File cover(Context ctx, String name) {
        if (name == null || !name.matches("\\d{1,12}-[SML]\\.jpg")) return null;
        File dir = new File(ctx.getFilesDir(), "covers");
        File f = new File(dir, name);
        if (f.exists() && f.length() > 0) return f;
        HttpURLConnection c = null;
        try {
            // ?default=false makes Open Library answer 404 instead of a blank placeholder image.
            URL u = new URL("https://covers.openlibrary.org/b/id/" + name + "?default=false");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            byte[] img = readAll(c.getInputStream());
            if (img.length < 200) return null;
            dir.mkdirs();
            File tmp = new File(dir, name + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(img);
            out.close();
            return tmp.renameTo(f) ? f : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }
}
