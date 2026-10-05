package vn.bantinsang;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class NewsWidgetService extends RemoteViewsService {
    public static final String EXTRA_FLIP = "flip";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getBooleanExtra(EXTRA_FLIP, false));
    }

    static class Row {
        NewsItem item;
        boolean world;
    }

    static class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final List<Row> rows = new ArrayList<>();
        private final boolean flip;

        Factory(Context ctx, boolean flip) { this.ctx = ctx; this.flip = flip; }

        @Override public void onCreate() { }

        @Override
        public void onDataSetChanged() {
            SharedPreferences wp = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE);
            boolean force = wp.getBoolean("force", false);
            wp.edit().putBoolean("force", false).apply();
            long age = System.currentTimeMillis() - NewsRepository.lastUpdated(ctx);
            List<NewsItem> vn, world;
            if (force || age > 30 * 60 * 1000L) {
                vn = NewsRepository.fetch(NewsRepository.VN);
                world = NewsRepository.fetch(NewsRepository.WORLD);
                NewsRepository.save(ctx, NewsRepository.VN, vn);
                NewsRepository.save(ctx, NewsRepository.WORLD, world);
            } else {
                vn = new ArrayList<>(); world = new ArrayList<>();
            }
            if (vn.isEmpty()) vn = NewsRepository.load(ctx, NewsRepository.VN);
            if (world.isEmpty()) world = NewsRepository.load(ctx, NewsRepository.WORLD);

            rows.clear();
            // Xen kẽ: 2 tin Việt Nam, 1 tin thế giới.
            int i = 0, j = 0;
            int max = flip ? 12 : 15;
            while (rows.size() < max && (i < vn.size() || j < world.size())) {
                for (int k = 0; k < 2 && i < vn.size(); k++) { Row r = new Row(); r.item = vn.get(i++); rows.add(r); }
                if (j < world.size()) { Row r = new Row(); r.item = world.get(j++); r.world = true; rows.add(r); }
            }
        }

        @Override public void onDestroy() { rows.clear(); }
        @Override public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (flip) return flipView(position);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            if (position >= rows.size()) return rv;
            Row r = rows.get(position);
            NewsItem n = r.item;
            rv.setTextViewText(R.id.w_title, n.title);
            String label = (r.world ? "Thế giới" : "Việt Nam") + " · " + n.source;
            rv.setTextViewText(R.id.w_meta, label);
            rv.setTextColor(R.id.w_meta, ctx.getColor(r.world ? R.color.world : R.color.vn));
            Bitmap b = ImageLoader.loadSync(n.imageUrl, 200);
            if (b != null) {
                rv.setImageViewBitmap(R.id.w_thumb, b);
                rv.setViewVisibility(R.id.w_thumb, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.w_thumb, View.GONE);
            }
            Intent fill = new Intent();
            fill.setData(Uri.parse(n.link));
            rv.setOnClickFillInIntent(R.id.w_row, fill);
            return rv;
        }

        /** Một "trang" của widget cuộn: ảnh lớn, tiêu đề đè lên ảnh. */
        private RemoteViews flipView(int position) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_flip_item);
            if (position >= rows.size()) return rv;
            Row r = rows.get(position);
            NewsItem n = r.item;
            int accent = ctx.getColor(r.world ? R.color.world_flip : R.color.vn_flip);
            rv.setInt(R.id.f_row, "setBackgroundColor", accent);
            rv.setTextViewText(R.id.f_title, n.title);
            rv.setTextViewText(R.id.f_meta, (r.world ? "THẾ GIỚI" : "VIỆT NAM") + " · " + n.source
                    + "   " + (position + 1) + "/" + rows.size());
            Bitmap b = ImageLoader.loadSync(n.imageUrl, 480);
            if (b != null) {
                rv.setImageViewBitmap(R.id.f_image, b);
                rv.setViewVisibility(R.id.f_image, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.f_image, View.GONE);
            }
            Intent fill = new Intent();
            fill.setData(Uri.parse(n.link));
            rv.setOnClickFillInIntent(R.id.f_row, fill);
            return rv;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }
    }
}
