package com.v3x.zekify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MatchReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
            && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SharedPreferences prefs = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet("scheduled_match_ids", Collections.emptySet());
        Set<String> ids = saved == null ? new HashSet<>() : new HashSet<>(saved);
        if (ids.isEmpty()) return;
        String raw = prefs.getString("scheduled_match_records", "{}");
        try {
            JSONObject records = new JSONObject(raw == null ? "{}" : raw);
            JSONArray matches = new JSONArray();
            for (String id : ids) { JSONObject savedMatch = records.optJSONObject(id); if (savedMatch != null) matches.put(savedMatch); }
            if (matches.length() == 0) { String cached = prefs.getString("matches_json", ""); if (cached != null && !cached.isEmpty()) matches = new JSONArray(cached); }
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.optJSONObject(i);
                if (match == null) continue;
                String id = match.optString("id", "");
                if (!ids.contains(id)) continue;
                long kickoff = match.optLong("date", 0L);
                if (kickoff <= System.currentTimeMillis()) continue;
                JSONObject teams = match.optJSONObject("teams");
                JSONObject home = teams == null ? null : teams.optJSONObject("home");
                JSONObject away = teams == null ? null : teams.optJSONObject("away");
                String homeName = home == null ? "Home" : home.optString("name", "Home");
                String awayName = away == null ? "Away" : away.optString("name", "Away");
                schedule(context, id, homeName + " vs " + awayName, kickoff - 900000L, 15, "reminder");
                schedule(context, id, homeName + " vs " + awayName, kickoff, 0, "kickoff");
            }
        } catch (Exception ignored) { }
    }

    private void schedule(Context context, String id, String title, long at, int minutes, String kind) {
        if (at <= System.currentTimeMillis()) return;
        Intent alarmIntent = new Intent(context, MatchReminderReceiver.class);
        alarmIntent.putExtra("matchId", id);
        alarmIntent.putExtra("matchTitle", title);
        alarmIntent.putExtra("kind", kind);
        int requestCode = id.hashCode() * 31 + minutes;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) { try { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); } catch (SecurityException ignored) { pending.cancel(); } } else pending.cancel();
    }
}
