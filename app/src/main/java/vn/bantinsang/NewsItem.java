package vn.bantinsang;

/** Một tin: tiêu đề, tóm tắt (sapo), ảnh, link bài gốc, tên báo. */
public class NewsItem {
    public String title = "";
    public String summary = "";
    public String imageUrl;
    public String link = "";
    public String source = "";
    public long time;

    public String timeLabel() {
        if (time <= 0) return "";
        long mins = (System.currentTimeMillis() - time) / 60000L;
        if (mins < 1) return "vừa xong";
        if (mins < 60) return mins + " phút trước";
        long hours = mins / 60;
        if (hours < 24) return hours + " giờ trước";
        return (hours / 24) + " ngày trước";
    }
}
