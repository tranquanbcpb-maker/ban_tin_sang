package vn.bantinsang;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dựng nội dung cho cả hai widget từ tin đã lưu, và tải tin mới khi cần.
 * Không dùng RemoteViewsService để chạy được trên mọi launcher.
 */
public class WidgetUpdater {

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final Locale VI = new Locale("vi", "VN");

    static class Row {
        NewsItem item;
        boolean world;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("widget", Context.MODE_PRIVATE);
    }

    static List<Row> rows(Context ctx, int max) {
        List<NewsItem> vn = NewsRepository.load(ctx, NewsRepository.VN);
        List<NewsItem> world = NewsRepository.load(ctx, NewsRepository.WORLD);
        List<Row> rows = new ArrayList<>();
        int i = 0, j = 0;
        while (rows.size() < max && (i < vn.size() || j < world.size())) {
            for (int k = 0; k < 2 && i < vn.size() && rows.size() < max; k++) {
                Row r = new Row(); r.item = vn.get(i++); rows.add(r);
            }
            if (j < world.size() && rows.size() < max) {
                Row r = new Row(); r.item = world.get(j++); r.world = true; rows.add(r);
            }
        }
        return rows;
    }

    static String status(Context ctx) {
        String s = prefs(ctx).getString("status", null);
        if (s != null) return s;
        long t = NewsRepository.lastUpdated(ctx);
        if (t <= 0) return ctx.getString(R.string.widget_empty);
        return ctx.getString(R.string.updated_at, new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t)));
    }

    static void setStatus(Context ctx, String s) {
        prefs(ctx).edit().putString("status", s).apply();
    }

    private static String today() {
        String d = new SimpleDateFormat("EEEE, d/M", VI).format(new Date());
        return d.substring(0, 1).toUpperCase(VI) + d.substring(1);
    }

    private static PendingIntent openLink(Context ctx, int code, String link) {
        Intent i = new Intent(ctx, OpenLinkActivity.class);
        i.setData(Uri.parse(link));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        return PendingIntent.getActivity(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refresh(Context ctx, Class<?> provider, String action) {
        Intent i = new Intent(ctx, provider).setAction(action);
        return PendingIntent.getBroadcast(ctx, provider.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String meta(Row r) {
        return (r.world ? "Thế giới" : "Việt Nam") + " · " + r.item.source;
    }

    // ---------- Widget danh sách ----------

    static void renderList(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, NewsWidgetProvider.class));
        if (ids.length == 0) return;
        List<Row> rows = rows(ctx, 8);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_news);
            rv.setTextViewText(R.id.widget_date, today());
            rv.setTextViewText(R.id.widget_status, status(ctx));
            rv.setOnClickPendingIntent(R.id.widget_header, openApp(ctx));
            rv.setOnClickPendingIntent(R.id.widget_refresh,
                    refresh(ctx, NewsWidgetProvider.class, NewsWidgetProvider.ACTION_REFRESH));
            rv.removeAllViews(R.id.widget_rows);
            int n = 0;
            for (Row r : rows) {
                RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
                item.setTextViewText(R.id.w_meta, meta(r));
                item.setTextColor(R.id.w_meta, ctx.getColor(r.world ? R.color.world : R.color.vn));
                item.setTextViewText(R.id.w_title, r.item.title);
                item.setOnClickPendingIntent(R.id.w_row, openLink(ctx, 1000 + id * 50 + n, r.item.link));
                rv.addView(R.id.widget_rows, item);
                n++;
            }
            m.updateAppWidget(id, rv);
        }
    }

    // ---------- Widget tự cuộn ----------

    static void renderFlip(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, FlipWidgetProvider.class));
        if (ids.length == 0) return;
        List<Row> rows = rows(ctx, 20);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_flipper);
            rv.setTextViewText(R.id.flip_status, status(ctx));
            rv.setOnClickPendingIntent(R.id.flip_header, openApp(ctx));
            rv.setOnClickPendingIntent(R.id.flip_refresh,
                    refresh(ctx, FlipWidgetProvider.class, FlipWidgetProvider.ACTION_REFRESH));
            rv.removeAllViews(R.id.flipper);
            if (rows.isEmpty()) {
                RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_flip_item);
                item.setViewVisibility(R.id.f_meta, View.GONE);
                item.setTextViewText(R.id.f_title, ctx.getString(R.string.widget_empty));
                item.setOnClickPendingIntent(R.id.f_row, openApp(ctx));
                rv.addView(R.id.flipper, item);
            }
            int n = 0;
            for (Row r : rows) {
                RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_flip_item);
                item.setTextViewText(R.id.f_meta, meta(r) + "   " + (n + 1) + "/" + rows.size());
                item.setTextColor(R.id.f_meta, ctx.getColor(r.world ? R.color.world : R.color.vn));
                item.setTextViewText(R.id.f_title, r.item.title);
                item.setOnClickPendingIntent(R.id.f_row, openLink(ctx, 5000 + id * 50 + n, r.item.link));
                rv.addView(R.id.flipper, item);
                n++;
            }
            m.updateAppWidget(id, rv);
        }
    }

    public static void renderAll(Context ctx) {
        try { renderList(ctx); } catch (Exception ignored) { }
        try { renderFlip(ctx); } catch (Exception ignored) { }
    }

    /** Tải tin mới ở luồng nền rồi vẽ lại widget. */
    public static void fetchAndRender(Context ctx, BroadcastReceiver.PendingResult pr) {
        final Context app = ctx.getApplicationContext();
        BG.execute(() -> {
            try {
                NewsRepository.lastError = null;
                List<NewsItem> vn = NewsRepository.fetch(NewsRepository.VN);
                List<NewsItem> world = NewsRepository.fetch(NewsRepository.WORLD);
                NewsRepository.save(app, NewsRepository.VN, vn);
                NewsRepository.save(app, NewsRepository.WORLD, world);
                if (vn.isEmpty() && world.isEmpty()) {
                    String err = NewsRepository.lastError;
                    setStatus(app, "Không tải được tin" + (err != null ? " (" + err + ")" : "") + ". Bấm ↻ để thử lại.");
                } else {
                    setStatus(app, null);
                }
            } catch (Throwable t) {
                setStatus(app, "Lỗi: " + t.getClass().getSimpleName());
            } finally {
                renderAll(app);
                if (pr != null) pr.finish();
            }
        });
    }

    static boolean isStale(Context ctx) {
        return System.currentTimeMillis() - NewsRepository.lastUpdated(ctx) > 30 * 60 * 1000L;
    }
}
