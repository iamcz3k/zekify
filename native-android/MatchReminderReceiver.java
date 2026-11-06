package com.v3x.zekify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Set;

public class MatchReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "zekify_match_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("matchId");
        String title = intent.getStringExtra("matchTitle");
        String kind = intent.getStringExtra("kind");
        if (id == null || id.isEmpty()) return;
        Set<String> scheduled = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE)
            .getStringSet("scheduled_match_ids", java.util.Collections.emptySet());
        if (scheduled == null || !scheduled.contains(id)) return;
        if ("reminder".equals(kind)) {
            show(context, id, title, "Kickoff in 15 minutes. Get ready to watch.", 1);
        } else if ("kickoff".equals(kind) || "kickoff-check".equals(kind)) {
            show(context, id, title, "Kickoff time. Tap to open Zekify.", 2);
        }
    }

    private void show(Context context, String id, String title, String message, int type) {
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
        manager.notify(id.hashCode() * 3 + type, builder.build());
    }
}
