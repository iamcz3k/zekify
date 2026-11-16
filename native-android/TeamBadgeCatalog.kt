package com.v3x.zekify

import android.content.Context
import org.json.JSONObject

/**
 * Stable-ID-first resolver for bundled team and competition badge assets.
 *
 * Add verified, licensed assets to assets/football/badges and register their
 * provider IDs in index.json. Missing entries deliberately fall back to the
 * match provider's genuine logo URL in MainActivity.
 */
object TeamBadgeCatalog {
    private const val INDEX_PATH = "football/badges/index.json"
    private val lock = Any()
    @Volatile private var cachedIndex: JSONObject? = null

    @JvmStatic
    fun resolveBundledTeamBadge(context: Context, team: JSONObject?): String =
        resolve(context, team, "teams")

    @JvmStatic
    fun resolveBundledCompetitionBadge(context: Context, competition: JSONObject?): String =
        resolve(context, competition, "competitions")

    private fun resolve(context: Context, record: JSONObject?, collection: String): String {
        if (record == null) return ""
        val id = firstValue(
            record,
            "id", "teamId", "team_id", "competitionId", "competition_id",
            "tournamentId", "tournament_id", "uid", "providerId", "provider_id", "slug"
        ) ?: return ""
        val index = loadIndex(context) ?: return ""
        val entries = index.optJSONObject(collection) ?: return ""
        val value = entries.opt(id) ?: return ""
        val path = when (value) {
            is JSONObject -> value.optString("path", "")
            is String -> value
            else -> ""
        }.trim()
        if (!path.startsWith("football/badges/") || path.contains("..") || path.startsWith("/")) return ""
        return try {
            context.assets.open(path).use { }
            "asset://$path"
        } catch (_: Exception) {
            ""
        }
    }

    private fun loadIndex(context: Context): JSONObject? {
        cachedIndex?.let { return it }
        synchronized(lock) {
            cachedIndex?.let { return it }
            return try {
                val parsed = context.assets.open(INDEX_PATH).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                cachedIndex = parsed
                parsed
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun firstValue(record: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val raw = record.opt(key) ?: continue
            val value = raw.toString().trim()
            if (value.isNotEmpty() && value != "null" && value != "0") return value
        }
        return null
    }
}
