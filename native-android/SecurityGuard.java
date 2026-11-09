package com.v3x.zekify;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.security.MessageDigest;
import java.util.Locale;

public final class SecurityGuard {
    private static final String OFFICIAL_PACKAGE = "com.v3x.zekify";
    private static final String OFFICIAL_CERT_SHA256 =
        "f8f7f6f4ee9dabfb6004af5862edffbe78fd48622d90a8ac6eb79128a63f7119";
    private static volatile boolean blockedDialogShown = false;
    private static final int P = 28;

    private SecurityGuard() {}

    public static boolean verify(Context context) {
        try {
            if (!OFFICIAL_PACKAGE.equals(context.getPackageName())) return false;
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= P) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return false;
                signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return false;
            for (Signature signature : signatures) {
                if (OFFICIAL_CERT_SHA256.equals(sha256(signature.toByteArray()))) return true;
            }
            return false;
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
        message.setText("This copy of Zekify is modified or not authentic. Please install the official Zekify application.");
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
            .setPositiveButton("Install the real application", (d, which) -> {\n                try {\n                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://zekify.vercel.app")));\n                } finally {\n                    activity.finishAffinity();\n                }\n            })
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
