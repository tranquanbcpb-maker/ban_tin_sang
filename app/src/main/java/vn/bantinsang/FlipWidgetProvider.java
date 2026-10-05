package vn.bantinsang;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** Widget "Bản tin cuộn": mỗi lần một dòng tin, tự chuyển sau 4 giây. */
public class FlipWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "vn.bantinsang.FLIP_REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        WidgetUpdater.renderFlip(ctx);
        if (WidgetUpdater.isStale(ctx)) WidgetUpdater.fetchAndRender(ctx, goAsync());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            WidgetUpdater.setStatus(ctx, ctx.getString(R.string.loading));
            WidgetUpdater.renderAll(ctx);
            WidgetUpdater.fetchAndRender(ctx, goAsync());
        }
    }
}
