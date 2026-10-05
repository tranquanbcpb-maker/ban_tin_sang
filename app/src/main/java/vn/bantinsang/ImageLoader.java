package vn.bantinsang;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tải ảnh tin từ trang báo, thu nhỏ và giữ trong bộ nhớ đệm. */
public class ImageLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void load(final String url, final ImageView view, final int maxPx) {
        view.setTag(url);
        if (url == null) { view.setImageDrawable(null); return; }
        Bitmap cached = CACHE.get(url + "#" + maxPx);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            final Bitmap b = loadSync(url, maxPx);
            MAIN.post(() -> {
                if (url.equals(view.getTag()) && b != null) view.setImageBitmap(b);
            });
        });
    }

    /** Dùng cho widget (đã chạy sẵn trên luồng nền). */
    public static Bitmap loadSync(String url, int maxPx) {
        if (url == null) return null;
        String key = url + "#" + maxPx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) BanTinSang/1.0");
            byte[] data;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
                data = bos.toByteArray();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx / 2) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b == null) return null;
            if (b.getWidth() > maxPx) {
                int h = Math.max(1, b.getHeight() * maxPx / b.getWidth());
                b = Bitmap.createScaledBitmap(b, maxPx, h, true);
            }
            CACHE.put(key, b);
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- Ảnh nhỏ lưu trên máy cho widget ----

    private static File thumbFile(Context ctx, String url) {
        File dir = new File(ctx.getCacheDir(), "thumbs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, Integer.toHexString(url.hashCode()) + ".jpg");
    }

    /** Đọc ảnh nhỏ đã lưu (không dùng mạng, gọi ở đâu cũng được). */
    public static Bitmap thumbFromDisk(Context ctx, String url) {
        if (url == null) return null;
        String key = "disk#" + url;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        File f = thumbFile(ctx, url);
        if (!f.exists()) return null;
        b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) CACHE.put(key, b);
        return b;
    }

    /** Tải ảnh, cắt vuông nhỏ và lưu vào máy (chạy ở luồng nền). */
    public static void cacheThumb(Context ctx, String url, int size) {
        if (url == null) return;
        File f = thumbFile(ctx, url);
        if (f.exists()) return;
        Bitmap src = loadSync(url, size * 2);
        if (src == null) return;
        int s = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        Bitmap out = Bitmap.createScaledBitmap(sq, size, size, true);
        try (FileOutputStream fo = new FileOutputStream(f)) {
            out.compress(Bitmap.CompressFormat.JPEG, 82, fo);
        } catch (Exception ignored) { }
    }
}
