package vn.bantinsang;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lấy tin từ RSS của VnExpress, Quân đội Nhân dân và Tuổi Trẻ. */
public class NewsRepository {

    public static final int VN = 0;
    public static final int WORLD = 1;

    static class Feed {
        final String url, source;
        final int max;
        Feed(String url, String source, int max) { this.url = url; this.source = source; this.max = max; }
    }

    static final Feed[] VN_FEEDS = {
            new Feed("https://vnexpress.net/rss/thoi-su.rss", "VnExpress", 6),
            new Feed("https://www.qdnd.vn/rss/cate/chinh-tri-3429.rss", "Quân đội Nhân dân", 4),
            new Feed("https://www.qdnd.vn/rss/cate/quoc-phong-an-ninh-3424.rss", "Quân đội Nhân dân", 3),
            new Feed("https://tuoitre.vn/rss/thoi-su.rss", "Tuổi Trẻ", 4),
            new Feed("https://vnexpress.net/rss/kinh-doanh.rss", "VnExpress", 3),
    };

    static final Feed[] WORLD_FEEDS = {
            new Feed("https://vnexpress.net/rss/the-gioi.rss", "VnExpress", 6),
            new Feed("https://www.qdnd.vn/rss/cate/quoc-te-3447.rss", "Quân đội Nhân dân", 4),
            new Feed("https://tuoitre.vn/rss/the-gioi.rss", "Tuổi Trẻ", 4),
    };

    private static final Pattern IMG = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** Tải tin của một mục (VN hoặc WORLD), gộp các báo, mới nhất lên đầu. */
    public static List<NewsItem> fetch(int section) {
        Feed[] feeds = section == VN ? VN_FEEDS : WORLD_FEEDS;
        ExecutorService pool = Executors.newFixedThreadPool(feeds.length);
        List<Future<List<NewsItem>>> futures = new ArrayList<>();
        for (Feed f : feeds) futures.add(pool.submit(() -> fetchFeed(f)));
        List<NewsItem> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Future<List<NewsItem>> fu : futures) {
            try {
                for (NewsItem n : fu.get()) {
                    if (seen.add(n.link)) all.add(n);
                }
            } catch (Exception ignored) { }
        }
        pool.shutdown();
        Collections.sort(all, (a, b) -> Long.compare(b.time, a.time));
        return all;
    }

    /** Lỗi gần nhất khi tải tin, để hiện cho người dùng biết. */
    public static volatile String lastError = null;

    static List<NewsItem> fetchFeed(Feed feed) {
        List<NewsItem> out = new ArrayList<>();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(feed.url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) BanTinSang/1.0");
            c.setInstanceFollowRedirects(true);
            try (InputStream in = c.getInputStream()) {
                XmlPullParser p = Xml.newPullParser();
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                p.setInput(in, null);
                NewsItem cur = null;
                String tag = null;
                int ev = p.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT && out.size() < feed.max) {
                    if (ev == XmlPullParser.START_TAG) {
                        tag = p.getName();
                        if ("item".equals(tag)) { cur = new NewsItem(); cur.source = feed.source; }
                        else if (cur != null && ("enclosure".equals(tag) || "media:content".equals(tag) || "media:thumbnail".equals(tag))) {
                            String u = p.getAttributeValue(null, "url");
                            if (u != null && cur.imageUrl == null) cur.imageUrl = u;
                        }
                    } else if (ev == XmlPullParser.TEXT) {
                        if (cur != null && tag != null) {
                            String t = p.getText();
                            if (t != null) {
                                switch (tag) {
                                    case "title": cur.title += t; break;
                                    case "link": cur.link += t; break;
                                    case "description": cur.summary += t; break;
                                    case "pubDate": cur.time = parseDate(t.trim()); break;
                                }
                            }
                        }
                    } else if (ev == XmlPullParser.END_TAG) {
                        if ("item".equals(p.getName()) && cur != null) {
                            finish(cur);
                            if (!cur.title.isEmpty() && cur.link.startsWith("http")) out.add(cur);
                            cur = null;
                        }
                        tag = null;
                    }
                    ev = p.next();
                }
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
        return out;
    }

    private static void finish(NewsItem n) {
        n.title = clean(n.title);
        n.link = n.link.trim();
        String d = n.summary.replace("<![CDATA[", "").replace("]]>", "");
        if (n.imageUrl == null) {
            Matcher m = IMG.matcher(d);
            if (m.find()) n.imageUrl = m.group(1);
        }
        if (n.imageUrl != null) {
            n.imageUrl = n.imageUrl.replace("&amp;", "&").replace(" ", "%20").trim();
        }
        n.summary = clean(d);
    }

    private static String clean(String s) {
        String t = Html.fromHtml(s.replace("<![CDATA[", "").replace("]]>", ""), Html.FROM_HTML_MODE_LEGACY).toString();
        t = t.replace('\uFFFC', ' ').replaceAll("\\s+", " ").trim();
        return t;
    }

    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
    };

    private static long parseDate(String s) {
        String fixed = s.replace("GMT+7", "+0700").replace("GMT+07:00", "+0700");
        for (String f : DATE_FORMATS) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(fixed).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    // ---- Lưu tạm để widget và lúc không có mạng vẫn có tin ----

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("news_cache", Context.MODE_PRIVATE);
    }

    public static void save(Context ctx, int section, List<NewsItem> items) {
        if (items.isEmpty()) return;
        JSONArray arr = new JSONArray();
        try {
            for (NewsItem n : items) {
                JSONObject o = new JSONObject();
                o.put("t", n.title); o.put("s", n.summary); o.put("i", n.imageUrl == null ? "" : n.imageUrl);
                o.put("l", n.link); o.put("src", n.source); o.put("time", n.time);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs(ctx).edit()
                .putString("sec" + section, arr.toString())
                .putLong("updated", System.currentTimeMillis())
                .apply();
    }

    public static List<NewsItem> load(Context ctx, int section) {
        List<NewsItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString("sec" + section, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NewsItem n = new NewsItem();
                n.title = o.optString("t"); n.summary = o.optString("s");
                String img = o.optString("i"); n.imageUrl = img.isEmpty() ? null : img;
                n.link = o.optString("l"); n.source = o.optString("src"); n.time = o.optLong("time");
                out.add(n);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static long lastUpdated(Context ctx) {
        return prefs(ctx).getLong("updated", 0);
    }
}
