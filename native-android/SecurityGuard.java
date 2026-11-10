package com.v3x.zekify;

import android.app.Activity;
import android.widget.FrameLayout;
import android.view.Window;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
import android.app.Dialog;
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

    static { System.loadLibrary("zekifyguard"); }
    private static native int nativeEnvironmentFlags();

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
            final int nativeFlags;
            try { nativeFlags = nativeEnvironmentFlags(); }
            catch (Throwable nativeFailure) { return fail("Native security verification is unavailable"); }
            if ((nativeFlags & 1) != 0) return fail("A process tracer was detected by native verification");
            if ((nativeFlags & 2) != 0) return fail("Runtime instrumentation was detected by native verification");
            if ((nativeFlags & 4) != 0) return fail("Native security inspection could not complete");
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
        // Check known runtime-hook framework entry classes without initializing them.
        String[] hookClasses = {
            "de.robv.android.xposed.XposedBridge",
            "org.lsposed.lspd.core.Main",
            "com.saurik.substrate.MS$2",
            "re.frida.server.Frida"
        };
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            for (String name : hookClasses) {
                try {
                    Class.forName(name, false, loader);
                    return "A known runtime hooking framework was detected";
                } catch (ClassNotFoundException ignored) {
                } catch (Throwable ignored) {
                }
            }
        }
        // Check common Frida gadget/server library locations.
        String[] hookFiles = {
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/lib/libfrida-gadget.so",
            "/system/lib64/libfrida-gadget.so",
            "/vendor/lib/libfrida-gadget.so",
            "/vendor/lib64/libfrida-gadget.so"
        };
        for (String path : hookFiles) {
            if (exists(path)) return "A known runtime hooking component was detected";
        }
        // Frida commonly creates identifiable worker threads.
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                String threadName = thread.getName();
                if (threadName != null) {
                    String value = threadName.toLowerCase(Locale.US);
                    if (value.contains("gum-js-loop") || value.contains("frida")
                        || value.contains("gadget")) {
                        return "A runtime instrumentation thread was detected";
                    }
                }
            }
        } catch (Throwable ignored) { }
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
        FrameLayout screen = new FrameLayout(activity);
        screen.setBackgroundColor(Color.BLACK);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(activity, 26), dp(activity, 30), dp(activity, 26), dp(activity, 26));
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.rgb(17, 17, 17));
        card.setCornerRadius(dp(activity, 28));
        card.setStroke(dp(activity, 1), 0x28ffffff);
        panel.setBackground(card);
        TextView eyebrow = new TextView(activity);
        eyebrow.setText("ZEKIFY  /  SECURITY");
        eyebrow.setTextColor(Color.rgb(255, 138, 61));
        eyebrow.setTextSize(10);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));
        TextView icon = new TextView(activity);
        icon.setText("⛨");
        icon.setTextColor(Color.rgb(255, 138, 61));
        icon.setTextSize(44);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(-1, dp(activity, 76));
        iconP.topMargin = dp(activity, 22);
        panel.addView(icon, iconP);
        TextView status = new TextView(activity);
        status.setText("APP ACCESS BLOCKED");
        status.setTextColor(Color.rgb(255, 138, 61));
        status.setTextSize(10);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(0x263b2619);
        statusBg.setCornerRadius(dp(activity, 30));
        status.setBackground(statusBg);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(-2, -2);
        statusP.topMargin = dp(activity, 14);
        panel.addView(status, statusP);
        TextView title = new TextView(activity);
        title.setText("Unofficial application detected");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(-1, -2);
        titleP.topMargin = dp(activity, 18);
        panel.addView(title, titleP);
        TextView message = new TextView(activity);
        message.setText("Zekify blocked this session because the app failed its integrity check. " + failureReason + ". Install the official application to continue.");
        message.setTextColor(0xffb7b7b7);
        message.setTextSize(13);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(activity, 3), 1f);
        LinearLayout.LayoutParams messageP = new LinearLayout.LayoutParams(-1, -2);
        messageP.topMargin = dp(activity, 12);
        panel.addView(message, messageP);
        TextView install = new TextView(activity);
        install.setText("Install official Zekify");
        install.setTextColor(0xff111111);
        install.setTextSize(14);
        install.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        install.setGravity(Gravity.CENTER);
        install.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.rgb(255, 138, 61));
        buttonBg.setCornerRadius(dp(activity, 16));
        install.setBackground(buttonBg);
        LinearLayout.LayoutParams installP = new LinearLayout.LayoutParams(-1, dp(activity, 54));
        installP.topMargin = dp(activity, 26);
        panel.addView(install, installP);
        TextView footer = new TextView(activity);
        footer.setText("Only the verified official build can access Zekify.");
        footer.setTextColor(0xff777777);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerP = new LinearLayout.LayoutParams(-1, -2);
        footerP.topMargin = dp(activity, 16);
        panel.addView(footer, footerP);
        FrameLayout.LayoutParams panelP = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelP.setMargins(dp(activity, 18), dp(activity, 24), dp(activity, 18), dp(activity, 24));
        screen.addView(panel, panelP);
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(screen);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> activity.finishAffinity());
        install.setOnClickListener(v -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://laczek.dpdns.org/zekify"))); }
            finally { activity.finishAffinity(); }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
