package com.v3x.zekify

import android.content.Context
import org.json.JSONArray

/**
 * Reads the last successful fixture snapshot without starting a network request.
 * The existing SharedPreferences key is retained for backwards compatibility.
 */
object OfflineMatchStore {
    @JvmStatic
    fun readMatches(context: Context, sport: String): JSONArray? {
        val normalizedSport = sport.trim().lowercase(java.util.Locale.ROOT)
        if (normalizedSport.isEmpty()) return null
        val raw = context.getSharedPreferences("zekify_cache", Context.MODE_PRIVATE)
            .getString("matches_json_$normalizedSport", null)
            ?: return null
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            null
        }
    }
}
