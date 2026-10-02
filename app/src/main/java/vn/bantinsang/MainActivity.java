package vn.bantinsang;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final List<List<NewsItem>> data = new ArrayList<>();
    private int section = NewsRepository.VN;
    private NewsAdapter adapter;
    private TextView tabVn, tabWorld, status, empty;
    private ProgressBar progress;
    private ListView list;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        data.add(NewsRepository.load(this, NewsRepository.VN));
        data.add(NewsRepository.load(this, NewsRepository.WORLD));

        TextView date = findViewById(R.id.date);
        String d = new SimpleDateFormat("EEEE, d/M/yyyy", new Locale("vi", "VN")).format(new Date());
        date.setText(d.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + d.substring(1));

        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);
        tabVn = findViewById(R.id.tab_vn);
        tabWorld = findViewById(R.id.tab_world);

        adapter = new NewsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> adapter.toggle(position));

        tabVn.setOnClickListener(v -> selectTab(NewsRepository.VN));
        tabWorld.setOnClickListener(v -> selectTab(NewsRepository.WORLD));
        findViewById(R.id.refresh).setOnClickListener(v -> refresh());

        selectTab(NewsRepository.VN);
        refresh();
    }

    private void selectTab(int s) {
        section = s;
        tabVn.setSelected(s == NewsRepository.VN);
        tabWorld.setSelected(s == NewsRepository.WORLD);
        adapter.expanded.clear();
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        updateEmpty();
    }

    private void refresh() {
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.loading);
        bg.execute(() -> {
            List<NewsItem> vn = NewsRepository.fetch(NewsRepository.VN);
            List<NewsItem> world = NewsRepository.fetch(NewsRepository.WORLD);
            NewsRepository.save(this, NewsRepository.VN, vn);
            NewsRepository.save(this, NewsRepository.WORLD, world);
            main.post(() -> {
                progress.setVisibility(View.GONE);
                if (!vn.isEmpty()) data.set(NewsRepository.VN, vn);
                if (!world.isEmpty()) data.set(NewsRepository.WORLD, world);
                if (vn.isEmpty() && world.isEmpty()) {
                    status.setText(R.string.offline);
                } else {
                    status.setText(getString(R.string.updated_at,
                            new SimpleDateFormat("HH:mm", Locale.US).format(new Date())));
                    updateWidgets();
                }
                adapter.expanded.clear();
                adapter.notifyDataSetChanged();
                updateEmpty();
            });
        });
    }

    private void updateEmpty() {
        empty.setVisibility(data.get(section).isEmpty() && progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void updateWidgets() {
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        int[] ids = m.getAppWidgetIds(new ComponentName(this, NewsWidgetProvider.class));
        if (ids.length > 0) m.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    private int accent() {
        return getColor(section == NewsRepository.VN ? R.color.vn : R.color.world);
    }

    class NewsAdapter extends BaseAdapter {
        final Set<Integer> expanded = new HashSet<>();

        void toggle(int pos) {
            if (!expanded.remove(pos)) expanded.add(pos);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.get(section).size(); }
        @Override public Object getItem(int i) { return data.get(section).get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_news, parent, false);
            final NewsItem n = data.get(section).get(pos);
            boolean open = expanded.contains(pos);

            TextView meta = v.findViewById(R.id.meta);
            TextView title = v.findViewById(R.id.title);
            ImageView thumb = v.findViewById(R.id.thumb);
            View detail = v.findViewById(R.id.detail);
            ImageView big = v.findViewById(R.id.big_image);
            TextView summary = v.findViewById(R.id.summary);
            Button read = v.findViewById(R.id.read);

            String t = n.timeLabel();
            meta.setText(t.isEmpty() ? n.source : n.source + " · " + t);
            meta.setTextColor(accent());
            title.setText(n.title);

            thumb.setVisibility(!open && n.imageUrl != null ? View.VISIBLE : View.GONE);
            if (!open) ImageLoader.load(n.imageUrl, thumb, 240);

            detail.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) {
                big.setVisibility(n.imageUrl != null ? View.VISIBLE : View.GONE);
                ImageLoader.load(n.imageUrl, big, 900);
                summary.setText(n.summary.isEmpty() ? getString(R.string.no_summary) : n.summary);
                read.setText(getString(R.string.read_original, n.source));
                read.getBackground().mutate().setTint(accent());
                read.setOnClickListener(b -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(n.link))); }
                    catch (Exception ignored) { }
                });
            }
            return v;
        }
    }
}
