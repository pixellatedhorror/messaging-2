package com.smriti.app;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * All data is one JSON document in the app's private storage (smriti.json). The page owns its shape;
 * this class only reads and writes it safely: write to a temp file, sync, then rename, keeping the
 * previous version as smriti.json.prev.
 */
public final class Store {
    private static final String FILE = "smriti.json";
    private static final Object LOCK = new Object();

    private Store() {}

    public static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    public static String load(Context ctx) {
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "";
            try {
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                in.close();
                return new String(buf, 0, off, "UTF-8");
            } catch (IOException e) {
                return "";
            }
        }
    }

    public static void save(Context ctx, String json) throws IOException {
        if (json == null || !json.trim().startsWith("{")) throw new IOException("Refusing to save invalid data");
        synchronized (LOCK) {
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(json.getBytes("UTF-8"));
            out.getFD().sync();
            out.close();
            File cur = new File(dir, FILE);
            File prev = new File(dir, FILE + ".prev");
            if (cur.exists()) { prev.delete(); cur.renameTo(prev); }
            if (!tmp.renameTo(cur)) throw new IOException("Could not save data");
        }
    }
}
