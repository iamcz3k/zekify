package com.v3x.zekify;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class LiveSourcePrefetchWorker extends Worker {
    private static final String API = "https://repo-seed-transplanter.lovable.app";
    private static final String[] SPORTS = {
        "football","basketball","tennis","cricket","rugby","motor-sports","hockey",
        "volleyball","baseball","american-football","badminton","fight","cycling",
        "handball","australian-football"
    };
    private final SharedPreferences prefs;

    public LiveSourcePrefetchWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        prefs = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE);
    }

    @NonNull @Override public Result doWork() {
        int matchesSeen = 0, cached = 0, successfulFeeds = 0;
        for (String sport : SPORTS) {
            if (isStopped()) return Result.retry();
            try {
                JSONObject feed = get("/api/public/football-streams?mode=matches&sport=" + enc(sport));
                JSONArray events = extractEvents(feed);
                successfulFeeds++;
                if (events == null) continue;
                for (int i = 0; i < events.length(); i++) {
                    if (isStopped()) return Result.retry();
                    JSONObject match = events.optJSONObject(i);
                    if (match == null || !isLive(match)) continue;
                    String id = match.optString("id", "");
                    if (id.isEmpty()) continue;
                    matchesSeen++;
                    try {
                        JSONObject detailResponse = get("/api/public/football-streams?mode=detail&sport="
                            + enc(sport) + "&id=" + enc(id));
                        JSONObject detail = detailResponse.optJSONObject("data");
                        JSONArray sources = detail == null ? null : detail.optJSONArray("sources");
                        if (sources == null || sources.length() == 0) continue;
                        JSONObject record = new JSONObject();
                        record.put("cached_at", System.currentTimeMillis());
                        record.put("detail", detail);
                        prefs.edit().putString(cacheKey(sport, id), record.toString()).apply();
                        cached++;
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        android.util.Log.i("ZekifySourceCache", "Live source prefetch complete: feeds="
            + successfulFeeds + ", live=" + matchesSeen + ", cached=" + cached);
        return successfulFeeds > 0 || matchesSeen == 0 ? Result.success() : Result.retry();
    }

    private JSONObject get(String path) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API + path).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (stream != null) {
                try (InputStream in = stream) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
            }
            if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONArray extractEvents(JSONObject response) {
        JSONArray events = response.optJSONArray("data");
        JSONObject nested = response.optJSONObject("data");
        if (events == null && nested != null) {
            events = nested.optJSONArray("matches");
            if (events == null) events = nested.optJSONArray("events");
            if (events == null) events = nested.optJSONArray("fixtures");
            if (events == null) events = nested.optJSONArray("results");
        }
        if (events == null) events = response.optJSONArray("matches");
        if (events == null) events = response.optJSONArray("events");
        if (events == null) events = response.optJSONArray("fixtures");
        if (events == null) events = response.optJSONArray("results");
        if (events == null) {
            JSONObject payload = response.optJSONObject("payload");
            if (payload != null) {
                events = payload.optJSONArray("matches");
                if (events == null) events = payload.optJSONArray("events");
                if (events == null) events = payload.optJSONArray("fixtures");
            }
        }
        return events;
    }

    private boolean isLive(JSONObject match) {
        String status = match.optString("status", "").trim().toLowerCase(Locale.ROOT);
        return status.equals("live") || status.equals("in play") || status.equals("live now")
            || status.equals("1h") || status.equals("2h") || status.equals("ht")
            || status.equals("q1") || status.equals("q2") || status.equals("q3") || status.equals("q4");
    }

    private String cacheKey(String sport, String id) {
        String encoded = Base64.encodeToString(id.getBytes(StandardCharsets.UTF_8),
            Base64.NO_WRAP | Base64.URL_SAFE);
        return "live_sources_v1_" + sport + "_" + encoded;
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}
