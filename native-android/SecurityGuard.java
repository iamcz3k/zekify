package com.v3x.zekify;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

public final class SecurityGuard {
    private static final String OFFICIAL_PACKAGE = "com.v3x.zekify";
    private static final String OFFICIAL_CERT_SHA256 =
        "f8f7f6f4ee9dabfb6004af5862edffbe78fd48622d90a8ac6eb79128a63f7119";
    private static final String EXPECTED_INTEGRITY_ASSET =
        "ZEKIFY-INTEGRITY-V1:official-native-build";
    private static volatile boolean blockedDialogShown = false;
    private static volatile String failureReason = "Application integrity check failed";
    private static final int P = 28;

    private SecurityGuard() {}

    public static boolean verify(Context context) {
        try {
            if (!OFFICIAL_PACKAGE.equals(context.getPackageName())) {
                return fail("Unexpected application identity");
            }
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= P) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return fail("Signing information is unavailable");
                signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) {
                return fail("Application signature is missing");
            }
            boolean trustedSigner = false;
            for (Signature signature : signatures) {
                if (OFFICIAL_CERT_SHA256.equals(sha256(signature.toByteArray()))) {
                    trustedSigner = true;
                    break;
                }
            }
            if (!trustedSigner) return fail("The signing certificate is not Zekify's official certificate");
            if (!verifyIntegrityAsset(context)) {
                return fail("The application integrity asset has been modified or is missing");
            }

            ApplicationInfo appInfo = context.getApplicationInfo();
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                return fail("A debuggable application build was detected");
            }
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return fail("An active debugger was detected");
            }
            String rootIssue = findRootIndicators();
            if (rootIssue != null) return fail(rootIssue);
            String hookIssue = findInstrumentationIndicators();
            if (hookIssue != null) return fail(hookIssue);
            return true;
        } catch (Throwable ignored) {
            return fail("The application integrity check could not be completed");
        }
    }

    private static boolean fail(String reason) {
        failureReason = reason;
        return false;
    }

    private static boolean verifyIntegrityAsset(Context context) {
        try (InputStream input = context.getAssets().open("zekify-integrity.bin");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String actual = new String(output.toByteArray(), "UTF-8");
            return EXPECTED_INTEGRITY_ASSET.equals(actual);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static boolean exists(String path) {
        try { return new File(path).exists(); } catch (Throwable ignored) { return false; }
    }

    private static String findRootIndicators() {
        String[] paths = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/system/priv-app/Magisk",
            "/sbin/.magisk", "/data/adb/magisk", "/data/adb/ksu",
            "/data/adb/ap", "/debug_ramdisk/.magisk"
        };
        for (String path : paths) {
            if (exists(path)) return "A rooted or modified Android environment was detected";
        }
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) {
            return "A test-key Android system build was detected";
        }
        return null;
    }

    private static String findInstrumentationIndicators() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.toLowerCase(Locale.US);
                if (value.contains("frida") || value.contains("gum-js")
                    || value.contains("xposed") || value.contains("substrate")
                    || value.contains("zygisk") || value.contains("riru")
                    || value.contains("lsposed") || value.contains("edxposed")
                    || value.contains("gadget")) {
                    return "Runtime instrumentation or hooking software was detected";
                }
            }
        } catch (Throwable ignored) { }
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    String pid = line.substring("TracerPid:".length()).trim();
                    if (!"0".equals(pid)) return "A process tracer was detected";
                    break;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    public static void showBlocked(Activity activity) {
        if (activity == null || activity.isFinishing() || blockedDialogShown) return;
        blockedDialogShown = true;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(activity, 26), dp(activity, 28), dp(activity, 26), dp(activity, 24));
        panel.setBackgroundColor(Color.rgb(12, 12, 12));

        TextView icon = new TextView(activity);
        icon.setText("⛨");
        icon.setTextColor(Color.rgb(255, 138, 61));
        icon.setTextSize(42);
        icon.setGravity(Gravity.CENTER);
        panel.addView(icon, new LinearLayout.LayoutParams(-1, dp(activity, 64)));

        TextView title = new TextView(activity);
        title.setText("Unofficial application detected");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(activity, 12);
        panel.addView(title, titleParams);

        TextView message = new TextView(activity);
        message.setText("Zekify blocked this session. " + failureReason + ". Install the official application to continue.");
        message.setTextColor(Color.LTGRAY);
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(activity, 12);
        panel.addView(message, messageParams);

        TextView status = new TextView(activity);
        status.setText("APP ACCESS BLOCKED");
        status.setTextColor(Color.rgb(255, 138, 61));
        status.setTextSize(11);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(activity, 22);
        panel.addView(status, statusParams);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(panel)
            .setCancelable(false)
            .setPositiveButton("Install the real application", (d, which) -> {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://zekify.vercel.app")));
                } finally {
                    activity.finishAffinity();
                }
            })
            .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> activity.finishAffinity());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(255, 138, 61));
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
