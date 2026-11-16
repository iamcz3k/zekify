package com.v3x.zekify

import android.content.Context
import org.json.JSONObject

/**
 * Global football identity resolver.
 *
 * Provider IDs are authoritative. When a provider has no stable ID in the
 * event payload, the resolver tries country-qualified names, aliases and
 * normalized slugs. Missing images are intentionally left to MainActivity's
 * existing real-URL fetch and persistent badge cache.
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
        val index = loadIndex(context) ?: return ""
        val entries = index.optJSONObject(collection) ?: return ""
        val candidates = LinkedHashSet<String>()

        listOf(
            "id", "teamId", "team_id", "competitionId", "competition_id",
            "tournamentId", "tournament_id", "leagueId", "league_id",
            "uid", "providerId", "provider_id", "slug"
        ).forEach { key -> firstValue(record, key)?.let(candidates::add) }

        val name = firstValue(record, "name", "teamName", "team_name", "displayName", "title", "league")
        val country = firstValue(record, "country", "countryName", "country_name", "area")
        if (!name.isNullOrBlank()) {
            val normalizedName = normalize(name)
            if (normalizedName.isNotBlank()) {
                if (!country.isNullOrBlank()) candidates.add(normalize(country) + "::" + normalizedName)
                candidates.add(normalizedName)
            }
        }

        record.optJSONArray("aliases")?.let { aliases ->
            for (i in 0 until aliases.length()) {
                val alias = aliases.optString(i, "")
                if (alias.isNotBlank()) candidates.add(normalize(alias))
            }
        }

        for (candidate in candidates) {
            val value = entries.opt(candidate) ?: continue
            val path = when (value) {
                is JSONObject -> value.optString("path", "")
                is String -> value
                else -> ""
            }.trim()
            if (!isSafeAssetPath(path)) continue
            try {
                context.assets.open(path).use { }
                return "asset://$path"
            } catch (_: Exception) {
                // Stale catalogue entry: continue to another identity key.
            }
        }

        val nested = record.optJSONObject("team") ?: record.optJSONObject("club")
            ?: record.optJSONObject("competition")
        if (nested != null && nested !== record) return resolve(context, nested, collection)
        return ""
    }

    private fun isSafeAssetPath(path: String): Boolean =
        path.startsWith("football/badges/") && !path.contains("..") && !path.startsWith("/")

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

    private fun firstValue(record: JSONObject, key: String): String? {
        val raw = record.opt(key) ?: return null
        val value = raw.toString().trim()
        return value.takeIf { it.isNotEmpty() && it != "null" && it != "0" }
    }

    private fun normalize(value: String): String =
        value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
}
