package vn.bantinsang;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Nhận link từ widget rồi mở bài trên trình duyệt (Android 14 không cho widget mở link trực tiếp). */
public class OpenLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri link = getIntent() != null ? getIntent().getData() : null;
        if (link != null) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, link);
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(view);
            } catch (Exception ignored) { }
        }
        finish();
    }
}
