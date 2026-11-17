package com.v3x.zekify

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native Kotlin team profile backed by Zekify's locally cached fixtures. */
class FootballEntityActivity : Activity() {
    private val backgroundColor = Color.rgb(7, 7, 7)
    private val surfaceColor = Color.rgb(20, 20, 20)
    private val mutedColor = Color.rgb(155, 155, 155)
    private val accentColor = Color.WHITE
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var entity: JSONObject
    private lateinit var entityName: String
    private lateinit var entityType: String
    private lateinit var sport: String
    private lateinit var badgeView: ImageView
    private lateinit var body: LinearLayout
    private var savedMatches = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entityName = intent.getStringExtra("entity_name") ?: "Team"
        entityType = intent.getStringExtra("entity_type") ?: "team"
        sport = intent.getStringExtra("sport") ?: "football"
        entity = try { JSONObject(intent.getStringExtra("entity_json") ?: "{}") } catch (_: Exception) { JSONObject() }
        savedMatches = OfflineMatchStore.readMatches(this, sport) ?: JSONArray()
        buildScreen()
        loadBadge()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = text("‹", 34, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(surfaceColor, 14)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text(if (entityType == "competition") "COMPETITION PROFILE" else "TEAM PROFILE", 10, accentColor, true))
        heading.addView(text(entityName, 17, Color.WHITE, true))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        body.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(surfaceColor, 20)
            setPadding(dp(20), dp(22), dp(20), dp(22))
        }
        val badgeFrame = FrameLayout(this).apply { background = rounded(Color.rgb(35, 35, 35), 50) }
        badgeView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        badgeFrame.addView(badgeView, FrameLayout.LayoutParams(-1, -1))
        hero.addView(badgeFrame, LinearLayout.LayoutParams(dp(88), dp(88)))
        hero.addView(text(entityName, 22, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(13), 0, dp(4))
        })
        val country = first(entity, "country", "countryName", "country_name", "area")
        hero.addView(text(country.ifBlank { if (entityType == "competition") "Football competition" else "Football team" }, 12, mutedColor, false).apply { gravity = Gravity.CENTER })

        val favourite = text(if (isFavourite()) "♥  Following" else "♡  Follow", 13, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(48, 32, 22), 14)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener {
                val following = !isFavourite()
                saveFavourite(following)
                text = if (following) "♥  Following" else "♡  Follow"
            }
        }
        hero.addView(favourite, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(17) })
        body.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        addTeamOverview()
        addRecentFormAndH2H()

        body.addView(text("Fixtures & results", 17, Color.WHITE, true).apply {
            setPadding(0, dp(25), 0, dp(10))
        })
        val related = findRelatedMatches()
        if (related.length() == 0) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(surfaceColor, 16)
                setPadding(dp(20), dp(22), dp(20), dp(22))
            }
            val emptyArtId = resources.getIdentifier("zekify_illustration_empty_fixtures", "drawable", packageName)
            if (emptyArtId != 0) {
                val art = ImageView(this).apply { setImageResource(emptyArtId); scaleType = ImageView.ScaleType.FIT_CENTER }
                empty.addView(art, LinearLayout.LayoutParams(dp(136), dp(104)))
            }
            empty.addView(text("No saved fixtures yet", 14, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("This section fills with the team's fixtures after Zekify loads matches online.", 11, mutedColor, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
            body.addView(empty)
        } else {
            for (index in 0 until related.length()) {
                val match = related.optJSONObject(index) ?: continue
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(surfaceColor, 14)
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                }
                card.addView(text(matchTitle(match), 14, Color.WHITE, true))
                val status = first(match, "status", "statusText", "state")
                val date = first(match, "date", "startTime", "start_time", "kickoff", "timestamp")
                card.addView(text(listOf(status, date).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Saved match" }, 11, mutedColor, false).apply {
                    setPadding(0, dp(6), 0, 0)
                })
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        val lastSaved = getSharedPreferences("zekify_cache", MODE_PRIVATE).getLong("matches_cached_at_$sport", 0L)
        if (lastSaved > 0L) {
            val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastSaved))
            body.addView(text("Last saved match update: $stamp", 10, mutedColor, false).apply {
                setPadding(0, dp(14), 0, 0)
            })
        }
        body.alpha = 0f
        body.translationY = dp(10).toFloat()
        body.animate().alpha(1f).translationY(0f).setDuration(260L).start()
    }

    private fun addTeamOverview() {
        body.addView(text("OVERVIEW", 10, Color.WHITE, true).apply { setPadding(0, dp(22), 0, dp(9)) })
        val fields = listOf(
            "Country" to first(entity, "country", "countryName", "country_name", "area"),
            "Competition" to first(entity, "league", "competition", "tournament"),
            "Stadium" to first(entity, "venue", "stadium", "ground"),
            "Manager" to first(entity, "manager", "coach", "headCoach"),
            "Founded" to first(entity, "founded", "yearFounded", "established"),
            "Sport" to sport
        ).filter { it.second.isNotBlank() }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surfaceColor, 15)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        if (fields.isEmpty()) {
            card.addView(text("Official team details will appear when supplied by the sports data provider.", 11, mutedColor, false).apply { setPadding(0, dp(8), 0, dp(8)) })
        } else fields.forEach { (label, value) ->
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            line.addView(text(label, 11, mutedColor, false), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text(value, 11, Color.WHITE, true))
            card.addView(line)
        }
        body.addView(card)
    }

    private fun addRecentFormAndH2H() {
        val related = findRelatedMatches()
        body.addView(text("RECENT FORM", 10, Color.WHITE, true).apply { setPadding(0, dp(22), 0, dp(9)) })
        if (related.length() == 0) {
            body.addView(text("Recent results will appear when the provider supplies fixtures.", 11, mutedColor, false))
        } else {
            for (i in 0 until minOf(5, related.length())) {
                val match = related.optJSONObject(i) ?: continue
                val title = matchTitle(match)
                val status = first(match, "status", "statusText", "state").ifBlank { "Fixture" }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(surfaceColor, 13)
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                }
                card.addView(text(title, 12, Color.WHITE, true))
                card.addView(text(status, 10, mutedColor, false).apply { setPadding(0, dp(4), 0, 0) })
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        body.addView(text("SQUAD & LINEUPS", 10, Color.WHITE, true).apply { setPadding(0, dp(22), 0, dp(9)) })
        val squad = entity.optJSONArray("players") ?: entity.optJSONArray("squad") ?: entity.optJSONArray("roster")
        if (squad == null || squad.length() == 0) {
            body.addView(text("Player and lineup details appear here when available from the provider.", 11, mutedColor, false))
        } else {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(surfaceColor, 14)
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            for (i in 0 until minOf(30, squad.length())) {
                val player = squad.optJSONObject(i)
                val name = if (player == null) squad.optString(i) else first(player, "name", "playerName", "displayName")
                if (name.isNotBlank()) card.addView(text(name, 11, Color.WHITE, false).apply { setPadding(0, dp(5), 0, dp(5)) })
            }
            body.addView(card)
        }
        body.addView(text("HEAD-TO-HEAD", 10, Color.WHITE, true).apply { setPadding(0, dp(22), 0, dp(9)) })
        val h2h = entity.optJSONArray("h2h") ?: entity.optJSONArray("headToHead") ?: entity.optJSONArray("head_to_head")
        if (h2h == null || h2h.length() == 0) {
            body.addView(text("Previous meetings will appear when the match provider supplies H2H data.", 11, mutedColor, false))
        } else for (i in 0 until minOf(10, h2h.length())) {
            val item = h2h.optJSONObject(i)
            val label = if (item == null) h2h.optString(i) else first(item, "name", "title", "match", "event")
            if (label.isNotBlank()) body.addView(text(label, 11, Color.WHITE, false).apply { setPadding(0, dp(5), 0, dp(5)) })
        }
    }

    private fun findRelatedMatches(): JSONArray {
        val result = JSONArray()
        val wantedId = first(entity, "id", "teamId", "team_id", "competitionId", "competition_id", "leagueId", "league_id", "providerId", "provider_id")
        val wantedName = normalize(entityName)
        for (index in 0 until savedMatches.length()) {
            val match = savedMatches.optJSONObject(index) ?: continue
            var found = false
            if (entityType == "competition") {
                val competition = match.optJSONObject("competition") ?: match.optJSONObject("leagueData") ?: match.optJSONObject("tournament")
                val id = first(competition, "id", "competitionId", "competition_id", "leagueId", "league_id")
                    .ifBlank { first(match, "competitionId", "competition_id", "leagueId", "league_id") }
                val name = first(competition, "name", "title", "displayName", "league")
                    .ifBlank { first(match, "league", "competitionName", "competition_name") }
                if (wantedId.isNotBlank() && wantedId == id) found = true
                if (wantedName.isNotBlank() && wantedName == normalize(name)) found = true
            } else {
                val teams = match.optJSONObject("teams")
                for (side in listOf("home", "away")) {
                    val team = teams?.optJSONObject(side) ?: match.optJSONObject(side)
                        ?: match.optJSONObject(side + "Team") ?: match.optJSONObject(side + "_team")
                    val id = first(team, "id", "teamId", "team_id", "providerId", "provider_id")
                    val name = first(team, "name", "teamName", "team_name", "displayName")
                    if (wantedId.isNotBlank() && wantedId == id) found = true
                    if (wantedName.isNotBlank() && wantedName == normalize(name)) found = true
                }
            }
            if (found) result.put(match)
        }
        return result
    }

    private fun matchTitle(match: JSONObject): String {
        val teams = match.optJSONObject("teams")
        val home = teams?.optJSONObject("home") ?: match.optJSONObject("home") ?: match.optJSONObject("homeTeam")
        val away = teams?.optJSONObject("away") ?: match.optJSONObject("away") ?: match.optJSONObject("awayTeam")
        val h = first(home, "name", "teamName", "team_name").ifBlank { first(match, "homeTeam", "home_team") }
        val a = first(away, "name", "teamName", "team_name").ifBlank { first(match, "awayTeam", "away_team") }
        return listOf(h, a).filter { it.isNotBlank() }.joinToString("  vs  ")
            .ifBlank { first(match, "title", "eventName", "name").ifBlank { "Football match" } }
    }

    private fun loadBadge() {
        val bundled = if (entityType == "competition") TeamBadgeCatalog.resolveBundledCompetitionBadge(this, entity) else TeamBadgeCatalog.resolveBundledTeamBadge(this, entity)
        val remote = first(entity, "badge", "badgeUrl", "badge_url", "logo", "logoUrl", "logo_url", "image", "imageUrl", "image_url", "crest", "crestUrl", "crest_url")
        val source = bundled.ifBlank { remote }
        if (source.isBlank()) return
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val bytes = if (source.startsWith("asset://")) {
                    assets.open(source.removePrefix("asset://")).use { it.readBytes() }
                } else {
                    val cache = File(getDir("badges", MODE_PRIVATE), Integer.toHexString(source.hashCode()) + ".img")
                    if (cache.exists()) cache.readBytes() else {
                        connection = URL(source).openConnection() as HttpURLConnection
                        connection!!.connectTimeout = 8000
                        connection!!.readTimeout = 10000
                        connection!!.instanceFollowRedirects = true
                        val data = connection!!.inputStream.use { it.readBytes() }
                        if (data.size <= 2 * 1024 * 1024) cache.writeBytes(data)
                        data
                    }
                }
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null && source.endsWith(".svg", true)) {
                    val picture = com.caverock.androidsvg.SVG.getFromString(String(bytes, Charsets.UTF_8)).renderToPicture()
                    val width = picture.width.coerceIn(1, 512)
                    val height = picture.height.coerceIn(1, 512)
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap!!).drawPicture(picture)
                }
                val loaded = bitmap
                if (loaded != null) mainHandler.post { if (!isFinishing) badgeView.setImageBitmap(loaded) }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun favouriteKey(): String = first(entity, "id", "teamId", "team_id", "competitionId", "competition_id", "leagueId", "league_id", "providerId", "provider_id").ifBlank { normalize(entityName) }
    private fun favouriteStoreKey(): String = if (entityType == "competition") "competitions" else "teams"
    private fun isFavourite(): Boolean = favouriteSet().contains(favouriteKey())
    private fun saveFavourite(enabled: Boolean) {
        val prefs = getSharedPreferences("zekify_favourites", MODE_PRIVATE)
        val values = favouriteSet().toMutableSet()
        if (enabled) values.add(favouriteKey()) else values.remove(favouriteKey())
        val editor = prefs.edit().putStringSet(favouriteStoreKey(), values)
        if (enabled) {
            entity.put("name", entityName)
            entity.put("sport", sport)
            val prefix = if (entityType == "team") "team_meta_" else "competition_meta_"
            editor.putString(prefix + favouriteKey(), entity.toString())
        }
        editor.apply()
    }
    private fun favouriteSet(): Set<String> =
        getSharedPreferences("zekify_favourites", MODE_PRIVATE).getStringSet(favouriteStoreKey(), emptySet()) ?: emptySet()

    private fun first(obj: JSONObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            val value = obj.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null" && value != "0") return value
        }
        return ""
    }
    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
