package com.margins.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Home-screen widget: one highlight a day, picked at random from everything you've saved.
 * The pick is remembered for the day, so it doesn't change every time you save something new.
 * Tap the passage to open it in Margins; tap ↻ for another one.
 */
public class DailyWidget extends AppWidgetProvider {
    static final String ACTION_SHUFFLE = "com.margins.app.SHUFFLE";
    private static final String PREFS = "widget";

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews v = build(ctx, false);
        for (int id : ids) mgr.updateAppWidget(id, v);
        scheduleMidnight(ctx);
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        if (ACTION_SHUFFLE.equals(intent.getAction())) {
            push(ctx, build(ctx, true));
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(midnightIntent(ctx));
    }

    /** Called whenever data changes, so a deleted or edited highlight doesn't linger on the home screen. */
    static void refresh(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            if (m == null || m.getAppWidgetIds(new ComponentName(ctx, DailyWidget.class)).length == 0) return;
            push(ctx, build(ctx, false));
        } catch (Exception ignored) { /* the widget must never break saving */ }
    }

    private static void push(Context ctx, RemoteViews v) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, DailyWidget.class));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static long today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR);
    }

    /** Today's highlight (or a new random one when shuffling). */
    static JSONObject pick(Context ctx, boolean shuffle) {
        List<JSONObject> hs = Store.highlights(Store.load(ctx));
        if (hs.isEmpty()) return null;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long day = today();
        String keep = p.getLong("day", 0) == day ? p.getString("id", "") : "";
        if (!shuffle && !keep.isEmpty()) for (JSONObject h : hs) if (keep.equals(h.optString("id"))) return h;

        Random rnd = new Random(shuffle ? System.nanoTime() : day * 7919L + hs.size());
        JSONObject h = hs.get(rnd.nextInt(hs.size()));
        // Avoid showing the same one twice in a row when there's a choice.
        String prev = p.getString("id", "");
        for (int tries = 0; hs.size() > 1 && prev.equals(h.optString("id")) && tries < 5; tries++) h = hs.get(rnd.nextInt(hs.size()));
        p.edit().putLong("day", day).putString("id", h.optString("id")).apply();
        return h;
    }

    static RemoteViews build(Context ctx, boolean shuffle) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        JSONObject h = pick(ctx, shuffle);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (h == null) {
            v.setTextViewText(R.id.quote, "Your highlights will show up here, one a day.\n\nSelect text in any app, then tap Margins.");
            v.setTextViewText(R.id.source, "");
        } else {
            v.setTextViewText(R.id.quote, "“" + h.optString("text") + "”");
            JSONObject s = Store.find(Store.load(ctx).optJSONArray("sources"), h.optString("sourceId"));
            String line = s == null ? "" : "— " + Store.title(s) + (s.optString("author").isEmpty() ? "" : ", " + s.optString("author"));
            v.setTextViewText(R.id.source, line);
            open.putExtra(MainActivity.EXTRA_HIGHLIGHT, h.optString("id"));
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        v.setOnClickPendingIntent(R.id.body, PendingIntent.getActivity(ctx, 1, open, flags));
        Intent sh = new Intent(ctx, DailyWidget.class).setAction(ACTION_SHUFFLE);
        v.setOnClickPendingIntent(R.id.shuffle, PendingIntent.getBroadcast(ctx, 2, sh, flags));
        return v;
    }

    /** Wake shortly after midnight to show the new day's highlight. */
    private static void scheduleMidnight(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 1); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        am.set(AlarmManager.RTC, c.getTimeInMillis(), midnightIntent(ctx));
    }

    private static PendingIntent midnightIntent(Context ctx) {
        Intent i = new Intent(ctx, DailyWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, DailyWidget.class)));
        return PendingIntent.getBroadcast(ctx, 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
