package com.v3x.zekify;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateHistoryActivity extends Activity {
    private static final int ORANGE = 0xffff8a3d;
    private static final int MUTED = 0xff999999;
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
            try { render(new JSONArray(cached)); status.setText("Release history · saved"); }
            catch (Exception ignored) { }
        }
        load();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x20ffffff); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(false); return t;
    }
    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(14));
        TextView back = text("‹", 30, Color.WHITE, false); back.setGravity(Gravity.CENTER);
        back.setBackground(shape(0xff191919, 16)); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(0, -2, 1); titleP.leftMargin = dp(12); top.addView(titleBox, titleP);
        titleBox.addView(text("Update Logs", 21, Color.WHITE, true));
        TextView subtitle = text("WHAT'S NEW IN ZEKIFY", 9, ORANGE, true);
        LinearLayout.LayoutParams subtitleP = new LinearLayout.LayoutParams(-1, -2); subtitleP.topMargin = dp(5); titleBox.addView(subtitle, subtitleP);
        root.addView(top);
        status = text("Loading release history…", 11, MUTED, false);
        status.setPadding(dp(20), dp(3), dp(20), dp(12)); root.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        entries = new LinearLayout(this); entries.setOrientation(LinearLayout.VERTICAL);
        entries.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(entries); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (!getSharedPreferences("zekify_cache", MODE_PRIVATE).getBoolean("reduce_motion", false)) {
            root.setAlpha(0f); root.setScaleX(.985f); root.setScaleY(.985f); root.setTranslationX(dp(22));
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(300L)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f,0f,0f,1f)).start();
        }
        if (!getSharedPreferences("zekify_cache", MODE_PRIVATE).getBoolean("reduce_motion", false)) {
            root.setAlpha(0f); root.setTranslationY(dp(14));
            root.animate().alpha(1f).translationY(0f).setDuration(280L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
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
                if (code < 200 || code >= 300) throw new Exception("Release service unavailable");
                JSONArray releases;
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    releases = new JSONArray(out.toString("UTF-8"));
                }
                prefs.edit().putString("releases", releases.toString()).apply();
                main.post(() -> { if (!isFinishing()) { render(releases); status.setText(releases.length() + " releases · newest first"); } });
            } catch (Exception error) {
                main.post(() -> { if (!isFinishing()) { status.setText("Couldn't refresh release history"); if (entries.getChildCount() == 0) showRetry(); } });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void showRetry() {
        TextView note = text("Release history is temporarily unavailable. Check your connection and try again.", 12, MUTED, false);
        note.setPadding(dp(14), dp(18), dp(14), dp(18)); entries.addView(note);
        Button retry = new Button(this); retry.setText("Retry"); retry.setAllCaps(false);
        retry.setTextColor(Color.BLACK); retry.setBackground(shape(ORANGE, 16));
        entries.addView(retry, new LinearLayout.LayoutParams(-1, dp(48))); retry.setOnClickListener(v -> load());
    }

    private void render(JSONArray releases) {
        entries.removeAllViews();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i); if (release == null) continue;
            String version = safeVersion(release.optString("tag_name", "Version"));
            String date = release.optString("published_at", "");
            if (date.length() >= 10) date = date.substring(0, 10); else date = "";
            LinkedHashMap<String, ArrayList<String>> notes = parseNotes(release.optString("body", ""));
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(15), dp(16), dp(16)); card.setBackground(shape(0xff111111, 20));
            LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(-1, -2);
            cardP.bottomMargin = dp(12); entries.addView(card, cardP);

            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView versionText = text(version, 18, Color.WHITE, true);
            heading.addView(versionText, new LinearLayout.LayoutParams(0, -2, 1));
            if (i == 0) {
                TextView latest = text("LATEST", 9, 0xffffb27d, true);
                latest.setPadding(dp(10), dp(6), dp(10), dp(6));
                latest.setBackground(shape(0xff382419, 18)); heading.addView(latest);
            }
            card.addView(heading);
            if (!date.isEmpty()) {
                TextView dateText = text(date, 10, 0xff858585, false);
                LinearLayout.LayoutParams dateP = new LinearLayout.LayoutParams(-1, -2);
                dateP.topMargin = dp(6); card.addView(dateText, dateP);
            }
            View divider = new View(this); divider.setBackgroundColor(0xff292929);
            LinearLayout.LayoutParams dividerP = new LinearLayout.LayoutParams(-1, dp(1));
            dividerP.topMargin = dp(13); dividerP.bottomMargin = dp(2); card.addView(divider, dividerP);

            int before = card.getChildCount();
            addSection(card, "Added", notes.get("Added"), 0xff83d6a4);
            addSection(card, "Fixed", notes.get("Fixed"), ORANGE);
            addSection(card, "Improved", notes.get("Improved"), 0xff8dbdff);
            addSection(card, "Removed", notes.get("Removed"), 0xffe28d8d);
            addSection(card, "Notes", notes.get("Notes"), 0xffb5b5b5);
            if (card.getChildCount() == before) {
                TextView empty = text("No release notes were provided for this version.", 11, 0xff858585, false);
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2); ep.topMargin = dp(12); card.addView(empty, ep);
            }
        }
        if (entries.getChildCount() == 0) showRetry();
    }

    private String safeVersion(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "");
        return safe.isEmpty() ? "Version" : safe;
    }

    private LinkedHashMap<String, ArrayList<String>> parseNotes(String raw) {
        LinkedHashMap<String, ArrayList<String>> result = new LinkedHashMap<>();
        result.put("Added", new ArrayList<>()); result.put("Fixed", new ArrayList<>());
        result.put("Improved", new ArrayList<>()); result.put("Removed", new ArrayList<>());
        result.put("Notes", new ArrayList<>());
        String body = sanitize(raw).replace("\r", "");
        String active = "";
        for (String original : body.split("\\n+")) {
            String line = original.trim();
            if (line.isEmpty()) continue;
            String heading = line.replaceAll("^[#\\s>*_-]+", "").replaceAll("[:*]+$", "").trim().toLowerCase(Locale.ROOT);
            String category = categoryFromHeading(heading);
            if (!category.isEmpty() && (line.startsWith("#") || line.endsWith(":") || line.matches("(?i)^(added|fixed|improved|removed|changes|notes)\\s*$"))) {
                active = category; continue;
            }
            String plain = cleanLine(line);
            if (plain.isEmpty() || isNoise(plain)) continue;
            String[] chunks = plain.split("(?i)(?=(?:added|fixed|improved|removed)\\s*[-:])");
            for (String chunk : chunks) {
                String item = chunk.trim(); if (item.isEmpty()) continue;
                java.util.regex.Matcher prefixed = java.util.regex.Pattern.compile("(?i)^(added|fixed|improved|removed)\\s*[-:]\\s*(.+)$").matcher(item);
                String target = active;
                if (prefixed.matches()) { target = categoryFromHeading(prefixed.group(1)); item = prefixed.group(2).trim(); }
                if (target.isEmpty()) target = inferCategory(item);
                if (target.isEmpty()) target = "Notes";
                addUnique(result.get(target), item);
            }
        }
        return result;
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("(?is)<details.*?</details>", "")
            .replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("\\[([^\\]]+)\\]\\((?:https?://|www\\.)[^)]+\\)", "$1")
            .replaceAll("(?i)https?://\\S+|www\\.\\S+", " ")
            .replaceAll("(?i)\\bgithub\\.com/\\S+", " ")
            .replaceAll("(?i)@[-A-Za-z0-9_]+", " ")
            .replaceAll("(?i)\\biamcz3k\\b|\\biam_czek\\b", " ")
            .replaceAll("(?i)^\\s*(full changelog|compare|release by|author|commits?)\\s*:.*$", "")
            .replaceAll("(?m)^\\s*[-*]?\\s*(full changelog|compare changes|generated by).*?$", "");
    }

    private String categoryFromHeading(String value) {
        if (value.matches(".*\\b(added|additions|new features|new)\\b.*")) return "Added";
        if (value.matches(".*\\b(fixed|fixes|bug fixes|bugs)\\b.*")) return "Fixed";
        if (value.matches(".*\\b(improved|improvements|changed|enhanced|performance)\\b.*")) return "Improved";
        if (value.matches(".*\\b(removed|removals|deprecated)\\b.*")) return "Removed";
        if (value.matches(".*\\b(notes|other changes)\\b.*")) return "Notes";
        return "";
    }

    private String inferCategory(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(fix|fixed|fixes|bug|crash|correct|restore|resolved)\\b.*")) return "Fixed";
        if (lower.matches(".*\\b(add|added|adds|introduc|new feature|support|launch)\\b.*")) return "Added";
        if (lower.matches(".*\\b(improv|redesign|preserv|performance|enhanc|faster|optim|updated|update)\\b.*")) return "Improved";
        if (lower.matches(".*\\b(remov|removed|drop|deprecat)\\b.*")) return "Removed";
        return "";
    }

    private String cleanLine(String value) {
        return value.replaceAll("(?m)^#+\\s*", "")
            .replaceAll("^\\s*(?:[-*•]+|\\d+[.)])\\s*", "")
            .replaceAll("[*_\\x60]", "").replaceAll("\\s+", " ").trim();
    }

    private boolean isNoise(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("full changelog") || lower.startsWith("compare changes")
            || lower.contains("github.com") || lower.contains("iamcz3k")
            || lower.startsWith("published by") || lower.startsWith("generated by");
    }

    private void addUnique(ArrayList<String> target, String value) {
        if (target == null) return;
        String clean = cleanLine(value);
        if (!clean.isEmpty() && !isNoise(clean) && !target.contains(clean)) target.add(clean);
    }

    private void addSection(LinearLayout card, String label, ArrayList<String> items, int accent) {
        if (items == null || items.isEmpty()) return;
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionP = new LinearLayout.LayoutParams(-1, -2); sectionP.topMargin = dp(12); card.addView(section, sectionP);
        TextView tag = text(label.toUpperCase(Locale.ROOT), 9, accent, true);
        tag.setPadding(dp(9), dp(6), dp(9), dp(6)); tag.setBackground(shape(0xff202020, 14));
        LinearLayout.LayoutParams tagP = new LinearLayout.LayoutParams(-2, -2); section.addView(tag, tagP);
        for (String item : items) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(-1, -2); rowP.topMargin = dp(8); section.addView(row, rowP);
            TextView dot = text("•", 15, accent, true); row.addView(dot, new LinearLayout.LayoutParams(dp(18), -2));
            TextView detail = text(item, 11, 0xffd0d0d0, false);
            detail.setLineSpacing(dp(2), 1f); row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }
}
