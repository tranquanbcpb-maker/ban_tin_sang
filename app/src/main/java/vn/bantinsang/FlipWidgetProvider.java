package vn.bantinsang;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Widget "Bản tin cuộn": mỗi lần một tin, ảnh lớn, tự chuyển tin sau vài giây. */
public class FlipWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "vn.bantinsang.FLIP_REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            Intent svc = new Intent(ctx, NewsWidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.putExtra(NewsWidgetService.EXTRA_FLIP, true);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_flipper);
            rv.setRemoteAdapter(R.id.flipper, svc);
            rv.setEmptyView(R.id.flipper, R.id.flip_empty);

            // Chạm vào tin đang hiện: mở bài gốc.
            Intent view = new Intent(ctx, OpenLinkActivity.class);
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            rv.setPendingIntentTemplate(R.id.flipper, PendingIntent.getActivity(ctx, 10, view,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

            // Chạm chỗ trống khi chưa có tin: mở app.
            Intent open = new Intent(ctx, MainActivity.class);
            rv.setOnClickPendingIntent(R.id.flip_empty, PendingIntent.getActivity(ctx, 11, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            Intent refresh = new Intent(ctx, FlipWidgetProvider.class).setAction(ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.flip_refresh, PendingIntent.getBroadcast(ctx, 12, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            mgr.updateAppWidget(id, rv);
        }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.flipper);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, FlipWidgetProvider.class));
            ctx.getSharedPreferences("widget", Context.MODE_PRIVATE).edit().putBoolean("force", true).apply();
            onUpdate(ctx, mgr, ids);
        }
    }
}
