package com.v3x.zekify;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MatchReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "zekify_match_reminders";
    private static final String API = "https://laczekstream.qzz.io";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("matchId");
        String title = intent.getStringExtra("matchTitle");
        String kind = intent.getStringExtra("kind");
        if (id == null || id.isEmpty()) return;
        if ("reminder".equals(kind)) {
            Set<String> scheduled = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE)
                .getStringSet("scheduled_match_ids", java.util.Collections.emptySet());
            if (scheduled != null && scheduled.contains(id)) {
                show(context, id, title, "Kickoff in 15 minutes. Get ready to watch.");
            }
            return;
        }
        if (!"kickoff-check".equals(kind)) return;
        PendingResult pending = goAsync();
        int attempt = intent.getIntExtra("attempt", 0);
        new Thread(() -> {
            try {
                Set<String> scheduled = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE)
                    .getStringSet("scheduled_match_ids", java.util.Collections.emptySet());
                if (scheduled == null || !scheduled.contains(id)) return;
                String status = fetchStatus(id);
                if ("live".equalsIgnoreCase(status)) {
                    show(context, id, title, "The match has started. Tap to open Zekify.");
                } else if (!"finished".equalsIgnoreCase(status) && !"completed".equalsIgnoreCase(status) && attempt < 90) {
                    scheduleNextCheck(context, intent, id, attempt + 1);
                }
            } catch (Exception ignored) {
                if (attempt < 90) scheduleNextCheck(context, intent, id, attempt + 1);
            } finally {
                pending.finish();
            }
        }, "zekify-match-check").start();
    }

    private String fetchStatus(String id) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API
            + "/api/public/football-streams?mode=matches&sport=football").openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Match API returned " + code);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONObject payload = new JSONObject(output.toString("UTF-8"));
                JSONArray matches = payload.optJSONArray("data");
                if (matches == null) return "";
                for (int i = 0; i < matches.length(); i++) {
                    JSONObject match = matches.optJSONObject(i);
                    if (match != null && id.equals(match.optString("id"))) return match.optString("status", "");
                }
                return "";
            }
        } finally {
            connection.disconnect();
        }
    }

    private void scheduleNextCheck(Context context, Intent original, String id, int attempt) {
        Intent next = new Intent(context, MatchReminderReceiver.class);
        next.putExtras(original);
        next.putExtra("attempt", attempt);
        int requestCode = id.hashCode() * 31;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, next,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60_000L, pending);
    }

    private void show(Context context, String id, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33
            && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Match reminders", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("matchId", id);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title == null || title.isEmpty() ? "Zekify match reminder" : title)
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setPriority(Notification.PRIORITY_HIGH);
        manager.notify(id.hashCode(), builder.build());
    }
}
