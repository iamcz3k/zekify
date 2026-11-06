package com.v3x.zekify;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ZekifyUpdateManager {
    private static final String RELEASE_API = "https://api.github.com/repos/iamcz3k/zekify/releases/latest";
    private static final String PREFS = "zekify_updates";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile boolean installPromptVisible = false;

    public static final class Update {
        public final String version;
        public final String notes;
        final String apkUrl;
        final String checksumUrl;
        Update(String version, String notes, String apkUrl, String checksumUrl) {
            this.version = version;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
        }
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
                try {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    installed = info.versionName == null ? "0.0.0" : info.versionName;
                } catch (Exception e) { installed = "0.0.0"; }
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
                if (compareVersions(latest, installed) > 0 && !apk.isEmpty() && !checksum.isEmpty()) {
                    update = new Update(latest, release.optString("body", "Bug fixes and improvements."), apk, checksum);
                } else if (compareVersions(latest, installed) > 0) {
                    error = "The latest release is missing its APK or checksum file.";
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not check for updates." : e.getMessage();
            }
            Update result = update;
            String failure = error;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) callback.onResult(result, failure);
            });
        });
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

    private static Dialog requiredPage(Activity activity, String title, String message, String buttonText, Runnable action) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(activity, 28), dp(activity, 34), dp(activity, 28), dp(activity, 34));
        page.setBackgroundColor(0xff090909);
        TextView brand = new TextView(activity);
        brand.setText("ZEKIFY  /  SYSTEM UPDATE");
        brand.setTextColor(0xffff8a3d);
        brand.setTextSize(12);
        brand.setTypeface(null, 1);
        page.addView(brand);
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
        button.setBackgroundColor(0xffff8a3d);
        LinearLayout.LayoutParams btnp = new LinearLayout.LayoutParams(-1, -2);
        btnp.topMargin = dp(activity, 30);
        page.addView(button, btnp);
        TextView foot = new TextView(activity);
        foot.setText("This required update must be installed before continuing to Zekify.");
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
        if (activity == null || activity.isFinishing() || update == null) return;
        String notes = update.notes == null || update.notes.trim().isEmpty() ? "Stability improvements and important fixes." : update.notes;
        if (notes.length() > 1200) notes = notes.substring(0, 1200) + "…";
        requiredPage(activity, "Update required", "Zekify " + update.version + " is ready.\n\n" + notes, "Download update", () -> download(activity, update));
    }

    public static void download(Activity activity, Update update) {
        if (activity == null || activity.isFinishing()) return;
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        TextView status = new TextView(activity);
        status.setText("Preparing secure download…");
        status.setTextColor(0xffbdbdbd);
        status.setTextSize(14);
        Dialog page = new Dialog(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(activity, 28), dp(activity, 34), dp(activity, 28), dp(activity, 34));
        layout.setBackgroundColor(0xff090909);
        TextView brand = new TextView(activity);
        brand.setText("ZEKIFY  /  SYSTEM UPDATE");
        brand.setTextColor(0xffff8a3d);
        brand.setTextSize(12);
        brand.setTypeface(null, 1);
        layout.addView(brand);
        TextView heading = new TextView(activity);
        heading.setText("Installing update " + update.version);
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
        actionButton.setText("Downloading…");
        actionButton.setTextColor(0xff111111);
        actionButton.setTextSize(15);
        actionButton.setTypeface(null, 1);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(dp(activity, 16), dp(activity, 17), dp(activity, 16), dp(activity, 17));
        actionButton.setBackgroundColor(0xffff8a3d);
        actionButton.setEnabled(false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(activity, 30);
        layout.addView(actionButton, actionParams);
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
            String failure = null;
            try {
                String expected = downloadText(update.checksumUrl).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                File shared = new File(activity.getCacheDir(), "shared");
                if (!shared.exists() && !shared.mkdirs()) throw new Exception("Could not create update folder.");
                apk = new File(shared, "zekify-update.apk");
                HttpURLConnection connection = (HttpURLConnection) new URL(update.apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "Zekify-Android-Updater");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk)) {
                    int total = connection.getContentLength();
                    byte[] buffer = new byte[16384];
                    int count, received = 0;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        received += count;
                        int value = total > 0 ? (int) Math.min(100, received * 100L / total) : 0;
                        String size = total > 0 ? String.format(Locale.ROOT, "%.1f / %.1f MB", received / 1048576f, total / 1048576f) : String.format(Locale.ROOT, "%.1f MB", received / 1048576f);
                        activity.runOnUiThread(() -> {
                            if (page.isShowing()) {
                                progress.setProgress(value);
                                status.setText(total > 0 ? "Downloading… " + value + "% · " + size : "Downloading… " + size);
                            }
                        });
                    }
                } finally { connection.disconnect(); }
                if (!apk.isFile() || apk.length() == 0) throw new Exception("The downloaded APK is empty.");
                String actual = sha256(apk);
                if (!actual.equals(expected)) throw new Exception("APK checksum verification failed. The file was rejected.");
                verifyPackageAndSignature(activity, apk);
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().putString("pending_apk", apk.getAbsolutePath()).putString("pending_version", update.version).apply();
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Update download failed. Tap Retry." : e.getMessage();
                if (apk != null) apk.delete();
            }
            String error = failure;
            File downloaded = apk;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (error != null) {
                    status.setText(error);
                    progress.setVisibility(android.view.View.GONE);
                    actionButton.setText("Retry download");
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> download(activity, update));
                } else {
                    status.setText("Download complete. Checksum, package ID and signing certificate verified.");
                    progress.setProgress(100);
                    actionButton.setText("Install update");
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> install(activity, downloaded));
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

    private static Set<String> certificateDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures != null) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                byte[] bytes = digest.digest(signature.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b));
                result.add(hex.toString());
            }
        }
        return result;
    }

    private static void verifyPackageAndSignature(Activity activity, File apk) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo current = pm.getPackageInfo(activity.getPackageName(), flags);
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName))
            throw new Exception("This APK is not a Zekify update.");
        Set<String> installed = certificateDigests(current);
        Set<String> incoming = certificateDigests(candidate);
        if (installed.isEmpty() || !installed.equals(incoming))
            throw new Exception("Signing certificate mismatch. Installed certificate: " + installed + ". Downloaded APK certificate: " + incoming + ". Android blocks updates signed with a different key.");
        if (candidate.versionName == null || compareVersions(candidate.versionName, current.versionName) <= 0)
            throw new Exception("The downloaded APK is not newer than the installed version.");
    }

    public static void resumeInstall(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        String path = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getString("pending_apk", "");
        if (path == null || path.isEmpty()) return;
        File apk = new File(path);
        if (!apk.isFile()) return;
        String version = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getString("pending_version", "");
        requiredPage(activity, "Update ready", "Zekify " + version + " has been downloaded and verified. Install it now to continue.", "Install update", () -> install(activity, apk));
    }

    private static void install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            requiredPage(activity, "Allow update installation", "Android requires permission for Zekify to install its verified updates. Open Android settings, allow installs from Zekify, then return here.", "Open Android settings", () -> {
                Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
            });
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(activity).setTitle("Could not open installer")
                .setMessage(e.getMessage() == null ? "Android could not start the package installer." : e.getMessage())
                .setPositiveButton("OK", null).show();
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
