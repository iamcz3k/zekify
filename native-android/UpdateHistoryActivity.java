package com.v3x.zekify;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateHistoryActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout entries;
    private TextView status;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SecurityGuard.verify(this)) { SecurityGuard.showBlocked(this); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = getSharedPreferences("zekify_update_history", MODE_PRIVATE);
        build();
        String cached = prefs.getString("releases", "");
        if (!cached.isEmpty()) {
            try { render(new JSONArray(cached)); status.setText("Release history · cached"); }
            catch (Exception ignored) { }
        }
        load();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), 0x20ffffff); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(null, 1); return t;
    }
    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(18), dp(16), dp(18), dp(14));
        TextView back = text("‹", 30, Color.WHITE, false); back.setGravity(Gravity.CENTER); back.setBackground(shape(0xff191919, 16));
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42))); back.setOnClickListener(v -> finish());
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(0, -2, 1); titleP.leftMargin = dp(12); top.addView(titleBox, titleP);
        titleBox.addView(text("Update Logs", 21, Color.WHITE, true));
        titleBox.addView(text("EVERY PUBLISHED VERSION", 9, 0xffff8a3d, true));
        root.addView(top);
        status = text("Loading release history…", 11, 0xff999999, false); status.setPadding(dp(20), dp(4), dp(20), dp(12)); root.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        entries = new LinearLayout(this); entries.setOrientation(LinearLayout.VERTICAL); entries.setPadding(dp(16), dp(4), dp(16), dp(22));
        scroll.addView(entries); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void load() {
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL("https://api.github.com/repos/iamcz3k/zekify/releases?per_page=100").openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Zekify-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("GitHub returned HTTP " + code);
                JSONArray releases;
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    releases = new JSONArray(out.toString("UTF-8"));
                }
                prefs.edit().putString("releases", releases.toString()).apply();
                main.post(() -> { if (!isFinishing()) { render(releases); status.setText(releases.length() + " published releases · newest first"); } });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Unable to load release history." : error.getMessage();
                main.post(() -> { if (!isFinishing()) { status.setText("Could not refresh · " + message); if (entries.getChildCount() == 0) showRetry(); } });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void showRetry() {
        TextView note = text("Release history is temporarily unavailable. Check your connection and retry.", 12, 0xffaaaaaa, false);
        note.setPadding(dp(14), dp(18), dp(14), dp(18)); entries.addView(note);
        Button retry = new Button(this); retry.setText("Retry"); retry.setAllCaps(false); retry.setTextColor(Color.BLACK); retry.setBackground(shape(0xffff8a3d, 16));
        entries.addView(retry, new LinearLayout.LayoutParams(-1, dp(48))); retry.setOnClickListener(v -> load());
    }

    private void render(JSONArray releases) {
        entries.removeAllViews();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i); if (release == null) continue;
            String version = release.optString("tag_name", "Unknown version");
            String body = release.optString("body", "").trim();
            String date = release.optString("published_at", ""); if (date.length() >= 10) date = date.substring(0, 10);
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(15), dp(14), dp(15), dp(14)); card.setBackground(shape(0xff151515, 20));
            LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(-1, -2); cardP.bottomMargin = dp(10); entries.addView(card, cardP);
            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(text(version, 16, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            if (i == 0) { TextView latest = text("LATEST", 9, 0xffffa66a, true); latest.setPadding(dp(9), dp(5), dp(9), dp(5)); latest.setBackground(shape(0xff382619, 20)); heading.addView(latest); }
            card.addView(heading);
            if (!date.isEmpty()) { TextView dateText = text(date, 10, 0xff888888, false); LinearLayout.LayoutParams dateP = new LinearLayout.LayoutParams(-1, -2); dateP.topMargin = dp(5); card.addView(dateText, dateP); }
            ArrayList<String> added = pick(body, "added"), improved = pick(body, "improved"), fixed = pick(body, "fixed"), removed = pick(body, "removed");
            boolean categorized = !added.isEmpty() || !improved.isEmpty() || !fixed.isEmpty() || !removed.isEmpty();
            if (!added.isEmpty()) section(card, "ADDED", added);
            if (!improved.isEmpty()) section(card, "IMPROVED", improved);
            if (!fixed.isEmpty()) section(card, "FIXED", fixed);
            if (!removed.isEmpty()) section(card, "REMOVED", removed);
            if (!categorized && !body.isEmpty()) { TextView notes = text(clean(body), 11, 0xffbdbdbd, false); LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(10); card.addView(notes, np); }
            if (body.isEmpty()) { TextView notes = text("Release notes were not published for this version.", 11, 0xff888888, false); LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(10); card.addView(notes, np); }
        }
    }

    private ArrayList<String> pick(String body, String kind) {
        ArrayList<String> result = new ArrayList<>();
        for (String part : body.replace("\r", "").split("(?<=[.!?])\\s+|\\n+")) {
            String s = clean(part).trim(); if (s.isEmpty()) continue; String lower = s.toLowerCase(Locale.ROOT);
            boolean match = kind.equals("added") ? lower.matches(".*\\b(add|adds|added|introduc|new|support|launch).*")
                : kind.equals("improved") ? lower.matches(".*\\b(improv|redesign|preserv|performance|enhanc|faster|optim).*")
                : kind.equals("fixed") ? lower.matches(".*\\b(fix|fixed|fixes|bug|crash|correct|restore).*")
                : lower.matches(".*\\b(remov|removed|drop|deprecat).*");
            if (match && !result.contains(s)) result.add(s);
        }
        return result;
    }
    private String clean(String value) { return value.replaceAll("(?m)^#+\\s*", "").replaceAll("[*_]", "").replaceAll("\\s+", " ").trim(); }
    private void section(LinearLayout card, String heading, ArrayList<String> lines) {
        TextView title = text(heading, 9, 0xffffa66a, true); LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.topMargin = dp(12); card.addView(title, hp);
        for (String line : lines) { TextView item = text("•  " + line, 11, 0xffc5c5c5, false); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(5); card.addView(item, p); }
    }
}
