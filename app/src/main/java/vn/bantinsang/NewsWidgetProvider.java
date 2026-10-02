package vn.bantinsang;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widget màn hình chính: danh sách tiêu đề tin, chạm để mở bài. */
public class NewsWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "vn.bantinsang.REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            Intent svc = new Intent(ctx, NewsWidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_news);
            rv.setRemoteAdapter(R.id.widget_list, svc);
            rv.setEmptyView(R.id.widget_list, R.id.widget_empty);

            String d = new SimpleDateFormat("EEEE, d/M", new Locale("vi", "VN")).format(new Date());
            rv.setTextViewText(R.id.widget_date, d.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + d.substring(1));

            // Chạm vào một tin: mở bài gốc trên trình duyệt.
            Intent view = new Intent(Intent.ACTION_VIEW);
            PendingIntent tmpl = PendingIntent.getActivity(ctx, 0, view,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            rv.setPendingIntentTemplate(R.id.widget_list, tmpl);

            // Chạm tiêu đề widget: mở app.
            Intent open = new Intent(ctx, MainActivity.class);
            rv.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(ctx, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            // Nút làm mới.
            Intent refresh = new Intent(ctx, NewsWidgetProvider.class).setAction(ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(ctx, 2, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            mgr.updateAppWidget(id, rv);
        }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, NewsWidgetProvider.class));
            ctx.getSharedPreferences("widget", Context.MODE_PRIVATE).edit().putBoolean("force", true).apply();
            onUpdate(ctx, mgr, ids);
        }
    }
}
