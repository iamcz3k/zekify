package com.v3x.zekify;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ZekifyUpdateManager {
    private static final String RELEASE_API = "https://api.github.com/repos/iamcz3k/zekify/releases/latest";
    private static final String PREFS = "zekify_updates";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile boolean installPromptVisible = false;
    private static final AtomicBoolean downloadRunning = new AtomicBoolean(false);
    private static final String UPDATE_CHANNEL = "zekify_updates_v1";
    private static final int UPDATE_NOTIFICATION_ID = 3525;

    public static final class Update {
        public final String version;
        public final long buildCode;
        public final String notes;
        final String apkUrl;
        final String checksumUrl;
        Update(String version, long buildCode, String notes, String apkUrl, String checksumUrl) {
            this.version = version;
            this.buildCode = buildCode;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
        }
        String notificationKey() { return version + ":" + buildCode; }
    }

    public interface CheckCallback {
        void onResult(Update update, String error);
    }

    private ZekifyUpdateManager() {}

    public static void check(Activity activity, CheckCallback callback) {
        IO.execute(() -> {
            Update update = null;
            String error = null;
            try {
                JSONObject release = getJson(RELEASE_API);
                String tag = release.optString("tag_name", "").trim();
                String latest = tag.startsWith("v") ? tag.substring(1) : tag;
                String installed;
                long installedBuildCode;
                try {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    installed = info.versionName == null ? "0.0.0" : info.versionName;
                    installedBuildCode = getVersionCode(info);
                } catch (Exception e) { installed = "0.0.0"; installedBuildCode = 0L; }
                String releaseNotes = release.optString("body", "Bug fixes and improvements.");
                long latestBuildCode = parseBuildCode(releaseNotes);
                boolean newer = latestBuildCode > 0L
                    ? latestBuildCode > installedBuildCode
                    : compareVersions(latest, installed) > 0;
                JSONArray assets = release.optJSONArray("assets");
                String apk = "", checksum = "";
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
                        String url = asset.optString("browser_download_url", "");
                        if (name.endsWith(".apk")) apk = url;
                        if (name.endsWith(".sha256") || name.endsWith(".sha256sum")) checksum = url;
                    }
                }
                if (newer && !apk.isEmpty() && !checksum.isEmpty()) {
                    update = new Update(latest, latestBuildCode, releaseNotes, apk, checksum);
                } else if (newer) {
                    error = "The latest release is missing its APK or checksum file.";
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not check for updates." : e.getMessage();
            }
            Update result = update;
            String failure = error;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    if (result != null) notifyUpdateAvailable(activity, result);
                    callback.onResult(result, failure);
                }
            });
        });
    }

    private static void notifyUpdateAvailable(Activity activity, Update update) {
        if (activity == null || update == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 33
            && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                UPDATE_CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("New Zekify versions and secure update links");
            manager.createNotificationChannel(channel);
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        if (update.notificationKey().equals(prefs.getString("last_notified_version", ""))) return;
        Intent openUpdate = new Intent(activity, MainActivity.class);
        openUpdate.setAction("com.v3x.zekify.OPEN_UPDATE");
        openUpdate.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(activity, UPDATE_NOTIFICATION_ID, openUpdate, flags);
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(activity, UPDATE_CHANNEL)
            : new Notification.Builder(activity);
        notification.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Zekify update available")
            .setContentText("Version " + update.version + " is ready. Tap to review and install.")
            .setStyle(new Notification.BigTextStyle()
                .bigText("Zekify " + update.version + " is ready. Tap to open the secure update centre."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_DEFAULT);
        manager.notify(UPDATE_NOTIFICATION_ID, notification.build());
        prefs.edit().putString("last_notified_version", update.notificationKey()).apply();
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("[.+-]");
        String[] b = right.replaceFirst("^[vV]", "").split("[.+-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (Exception ignored) { return 0; }
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Zekify-Android-Updater");
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new JSONObject(output.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }

    private static GradientDrawable rounded(Activity activity, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(activity, radius));
        return shape;
    }

    private static Dialog requiredPage(Activity activity, String title, String message, String buttonText, Runnable action) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(activity, 28), dp(activity, 34), dp(activity, 28), dp(activity, 34));
        page.setBackgroundColor(0xff090909);
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.addView(new BrandMark(activity), new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));
        TextView brand = new TextView(activity);
        brand.setText("ZEKIFY");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(15);
        brand.setLetterSpacing(0.12f);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(-2, -2);
        brandTextParams.leftMargin = dp(activity, 12);
        brandRow.addView(brand, brandTextParams);
        TextView brandSub = new TextView(activity);
        brandSub.setText("UPDATE CENTRE");
        brandSub.setTextColor(0xffff8a3d);
        brandSub.setTextSize(10);
        brandSub.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams brandSubParams = new LinearLayout.LayoutParams(-2, -2);
        brandSubParams.leftMargin = dp(activity, 10);
        brandRow.addView(brandSub, brandSubParams);
        page.addView(brandRow);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(0xffffffff);
        heading.setTextSize(27);
        heading.setTypeface(null, 1);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(activity, 24);
        page.addView(heading, hp);
        TextView body = new TextView(activity);
        body.setText(message);
        body.setTextColor(0xffbdbdbd);
        body.setTextSize(15);
        body.setLineSpacing(dp(activity, 4), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(activity, 14);
        page.addView(body, bp);
        TextView button = new TextView(activity);
        button.setText(buttonText);
        button.setTextColor(0xff111111);
        button.setTextSize(15);
        button.setTypeface(null, 1);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 16), dp(activity, 17), dp(activity, 16), dp(activity, 17));
        button.setBackground(rounded(activity, 0xffff8a3d, 16));
        button.setElevation(dp(activity, 2));
        LinearLayout.LayoutParams btnp = new LinearLayout.LayoutParams(-1, -2);
        btnp.topMargin = dp(activity, 30);
        page.addView(button, btnp);
        TextView websiteLink = new TextView(activity);
        websiteLink.setText("Alternatively, download Zekify from our website");
        websiteLink.setTextColor(0xffff8a3d);
        websiteLink.setTextSize(13);
        websiteLink.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams websiteParams = new LinearLayout.LayoutParams(-1, -2);
        websiteParams.topMargin = dp(activity, 16);
        page.addView(websiteLink, websiteParams);
        websiteLink.setOnClickListener(v -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://laczek.dpdns.org/zekify"))); }
            catch (Exception ignored) { android.widget.Toast.makeText(activity, "Could not open the website.", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        TextView foot = new TextView(activity);
        foot.setText("SECURE UPDATE  ·  VERIFIED BY ZEKIFY");
        foot.setTextColor(0xff777777);
        foot.setTextSize(12);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(activity, 20);
        page.addView(foot, fp);
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(page);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        button.setOnClickListener(v -> { dialog.dismiss(); if (action != null) action.run(); });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    public static void showRequiredUpdate(Activity activity, Update update) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || update == null) return;
        if (installPromptVisible) return;
        installPromptVisible = true;
        Dialog dialog = requiredPage(activity, "Update available", "Zekify " + update.version + " is ready. Download the verified update and Android will handle installation.", "Update app", () -> download(activity, update));
        dialog.setOnDismissListener(ignored -> installPromptVisible = false);
    }

    public static void download(Activity activity, Update update) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || update == null) return;
        if (!downloadRunning.compareAndSet(false, true)) {
            android.widget.Toast.makeText(activity, "An update download is already running.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        TextView status = new TextView(activity);
        status.setText("Preparing secure download and verification…");
        status.setTextColor(0xffbdbdbd);
        status.setTextSize(14);
        Dialog page = new Dialog(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(activity, 28), dp(activity, 34), dp(activity, 28), dp(activity, 34));
        layout.setBackgroundColor(0xff090909);
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.addView(new BrandMark(activity), new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));
        TextView brand = new TextView(activity);
        brand.setText("ZEKIFY");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(15);
        brand.setLetterSpacing(0.12f);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(-2, -2);
        brandTextParams.leftMargin = dp(activity, 12);
        brandRow.addView(brand, brandTextParams);
        TextView brandSub = new TextView(activity);
        brandSub.setText("UPDATE CENTRE");
        brandSub.setTextColor(0xffff8a3d);
        brandSub.setTextSize(10);
        brandSub.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams brandSubParams = new LinearLayout.LayoutParams(-2, -2);
        brandSubParams.leftMargin = dp(activity, 10);
        brandRow.addView(brandSub, brandSubParams);
        layout.addView(brandRow);
        TextView heading = new TextView(activity);
        heading.setText("Downloading Zekify " + update.version);
        heading.setTextColor(0xffffffff);
        heading.setTextSize(25);
        heading.setTypeface(null, 1);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(activity, 24);
        layout.addView(heading, headingParams);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(activity, 14);
        layout.addView(status, statusParams);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(activity, 8));
        progressParams.topMargin = dp(activity, 22);
        layout.addView(progress, progressParams);
        TextView actionButton = new TextView(activity);
        actionButton.setText("Downloading update…");
        actionButton.setTextColor(0xff111111);
        actionButton.setTextSize(15);
        actionButton.setTypeface(null, 1);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(dp(activity, 16), dp(activity, 17), dp(activity, 16), dp(activity, 17));
        actionButton.setBackground(rounded(activity, 0xffff8a3d, 16));
        actionButton.setElevation(dp(activity, 2));
        actionButton.setEnabled(false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(activity, 30);
        layout.addView(actionButton, actionParams);
        TextView websiteLink = new TextView(activity);
        websiteLink.setText("Alternatively, download Zekify from our website");
        websiteLink.setTextColor(0xffff8a3d);
        websiteLink.setTextSize(13);
        websiteLink.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams websiteParams = new LinearLayout.LayoutParams(-1, -2);
        websiteParams.topMargin = dp(activity, 16);
        layout.addView(websiteLink, websiteParams);
        websiteLink.setOnClickListener(v -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://laczek.dpdns.org/zekify"))); }
            catch (Exception ignored) { android.widget.Toast.makeText(activity, "Could not open the website.", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        page.setContentView(layout);
        page.setCancelable(false);
        page.setCanceledOnTouchOutside(false);
        page.show();
        Window pageWindow = page.getWindow();
        if (pageWindow != null) {
            pageWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            pageWindow.setBackgroundDrawableResource(android.R.color.transparent);
        }
        IO.execute(() -> {
            File apk = null;
            File partial = null;
            String failure = null;
            try {
                String checksumText = downloadText(update.checksumUrl).trim();
                java.util.regex.Matcher checksumMatcher = java.util.regex.Pattern.compile("(?i)\\b([a-f0-9]{64})\\b").matcher(checksumText);
                if (!checksumMatcher.find()) throw new Exception("The update checksum file is invalid.");
                String expected = checksumMatcher.group(1).toLowerCase(Locale.ROOT);
                File shared = new File(activity.getCacheDir(), "shared");
                if (!shared.exists() && !shared.mkdirs()) throw new Exception("Could not create update folder.");
                apk = new File(shared, "zekify-update.apk");
                partial = new File(shared, "zekify-update.apk.part");
                if (partial.exists() && !partial.delete()) throw new Exception("Could not clear an incomplete previous download.");
                HttpURLConnection connection = (HttpURLConnection) new URL(update.apkUrl).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "Zekify-Android-Updater");
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream;q=0.9, */*;q=0.8");
                try {
                    int response = connection.getResponseCode();
                    if (response < 200 || response >= 300) throw new Exception("APK download server returned HTTP " + response + ".");
                    long total = connection.getHeaderFieldLong("Content-Length", -1L);
                    if (total > 300L * 1024L * 1024L) throw new Exception("The update APK is unexpectedly large.");
                    try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial, false)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        long received = 0L;
                        int lastPercent = -1;
                        long lastUnknownReport = 0L;
                        while ((count = input.read(buffer)) != -1) {
                            if (count == 0) continue;
                            output.write(buffer, 0, count);
                            received += count;
                            if (received > 300L * 1024L * 1024L) throw new Exception("The update APK exceeded the safe download limit.");
                            int value = total > 0 ? (int) Math.min(100L, received * 100L / total) : -1;
                            boolean report = total > 0 ? value != lastPercent : received - lastUnknownReport >= 1024L * 1024L;
                            if (report) {
                                lastPercent = value;
                                lastUnknownReport = received;
                                String size = total > 0
                                    ? String.format(Locale.ROOT, "%.1f / %.1f MB", received / 1048576f, total / 1048576f)
                                    : String.format(Locale.ROOT, "%.1f MB downloaded", received / 1048576f);
                                final int progressValue = value < 0 ? 0 : value;
                                final String progressText = total > 0 ? "Downloading… " + value + "% · " + size : "Downloading… " + size;
                                activity.runOnUiThread(() -> {
                                    if (page.isShowing() && !activity.isFinishing()) {
                                        if (total > 0) progress.setProgress(progressValue);
                                        status.setText(progressText);
                                    }
                                });
                            }
                        }
                        output.flush();
                        output.getFD().sync();
                        if (total > 0 && received != total) throw new Exception("The APK download was incomplete. Please retry on a stable connection.");
                    }
                } finally {
                    connection.disconnect();
                }
                if (!partial.isFile() || partial.length() < 1024L * 1024L) throw new Exception("The downloaded APK is incomplete or empty.");
                String actual = sha256(partial);
                if (!actual.equals(expected)) throw new Exception("APK checksum verification failed. The file was rejected.");
                verifyPackageAndVersion(activity, partial);
                if (apk.exists() && !apk.delete()) throw new Exception("Could not replace the previous update file.");
                if (!partial.renameTo(apk)) throw new Exception("Could not finalize the verified update file.");
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit()
                    .putString("pending_apk", apk.getAbsolutePath())
                    .putString("pending_version", update.version)
                    .putLong("pending_build_code", update.buildCode)
                    .apply();
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Update download failed. Tap Retry." : e.getMessage();
                if (partial != null && partial.exists()) partial.delete();
                if (apk != null && apk.exists() && apk.length() == 0) apk.delete();
            }
            String error = failure;
            File downloaded = apk;
            activity.runOnUiThread(() -> {
                downloadRunning.set(false);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (error != null) {
                    status.setText(error);
                    progress.setVisibility(android.view.View.GONE);
                    actionButton.setText("Retry update");
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> download(activity, update));
                } else {
                    status.setText("Download complete. Checksum and package verified. Opening Android installer…");
                    progress.setProgress(100);
                    actionButton.setText("Opening installer…");
                    actionButton.setEnabled(false);
                    page.dismiss();
                    install(activity, downloaded);
                }
            });
        });
    }

    private static String downloadText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "Zekify-Android-Updater");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } finally { connection.disconnect(); }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b));
        return result.toString();
    }

    private static long getVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static long parseBuildCode(String notes) {
        if (notes == null) return 0L;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?im)^\\s*Build code\\s*:\\s*(\\d+)\\s*$").matcher(notes);
        if (!matcher.find()) return 0L;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    // The Android package installer is the authority for signing-certificate compatibility.
    // Zekify verifies the download checksum and package identity, then hands installation to Android.
    private static void verifyPackageAndVersion(Activity activity, File apk) throws Exception {
        PackageManager pm = activity.getPackageManager();
        PackageInfo current = pm.getPackageInfo(activity.getPackageName(), 0);
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName))
            throw new Exception("This APK is not a Zekify update.");
        if (candidate.versionName == null || getVersionCode(candidate) <= getVersionCode(current))
            throw new Exception("The downloaded APK is not newer than the installed build.");
    }

    public static void resumeInstall(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        String path = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getString("pending_apk", "");
        if (path == null || path.isEmpty()) return;
        File apk = new File(path);
        if (!apk.isFile()) return;
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String version = prefs.getString("pending_version", "");
        long pendingBuildCode = prefs.getLong("pending_build_code", 0L);
        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            boolean alreadyInstalled = pendingBuildCode > 0L
                ? getVersionCode(installed) >= pendingBuildCode
                : version != null && !version.isEmpty() && compareVersions(installed.versionName, version) >= 0;
            if (alreadyInstalled) {
                prefs.edit().remove("pending_apk").remove("pending_version").remove("pending_build_code").remove("awaiting_install_permission").apply();
                apk.delete();
                return;
            }
        } catch (Exception ignored) { }
        if (activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getBoolean("awaiting_install_permission", false)) {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().remove("awaiting_install_permission").apply();
            install(activity, apk);
            return;
        }
        requiredPage(activity, "Update ready", "Zekify " + version + " has been downloaded. Android will verify it when you install.", "Install update", () -> install(activity, apk));
    }

    private static void install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().putBoolean("awaiting_install_permission", true).apply();
            requiredPage(activity, "Allow update installation", "Android requires permission for Zekify to install its verified updates. Open Android settings, allow installs from Zekify, then return here.", "Open Android settings", () -> {
                Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
            });
            return;
        }
        try {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().remove("awaiting_install_permission").apply();
            if (apk == null || !apk.isFile() || apk.length() == 0) throw new Exception("The verified update file is no longer available. Download it again.");
            verifyPackageAndVersion(activity, apk);
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), "Zekify update", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.setClipData(ClipData.newUri(activity.getContentResolver(), "Zekify update", uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (intent.resolveActivity(activity.getPackageManager()) == null) throw new Exception("No Android package installer is available on this device.");
            activity.startActivity(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(activity).setTitle("Could not open installer")
                .setMessage(e.getMessage() == null ? "Android could not start the package installer." : e.getMessage())
                .setPositiveButton("OK", null).show();
        }
    }

    private static final class BrandMark extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BrandMark(Activity activity) { super(activity); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.45f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xfff4f4f4);
            canvas.drawCircle(cx, cy, radius, paint);
            Path pentagon = new Path();
            pentagon.moveTo(cx, cy-radius*.42f);
            pentagon.lineTo(cx+radius*.34f, cy-radius*.12f);
            pentagon.lineTo(cx+radius*.21f, cy+radius*.30f);
            pentagon.lineTo(cx-radius*.21f, cy+radius*.30f);
            pentagon.lineTo(cx-radius*.34f, cy-radius*.12f);
            pentagon.close();
            paint.setColor(0xffff8a3d);
            canvas.drawPath(pentagon, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, radius*.055f));
            paint.setColor(0xff171717);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
