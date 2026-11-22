package com.v3x.zekify

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Offline-first native list of the teams and competitions the user follows. */
class FollowedEntitiesActivity : Activity() {
    private val bg = Color.rgb(7, 7, 7)
    private val panel = Color.rgb(20, 20, 20)
    private val muted = Color.rgb(155, 155, 155)
    private val orange = Color.WHITE
    private var searchQuery = ""
    private lateinit var listHost: LinearLayout
    private var activeTab = "teams"
    private var matches = JSONArray()
    private var teams = linkedMapOf<String, JSONObject>()
    private var competitions = linkedMapOf<String, JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 7, 7)
        window.navigationBarColor = bg
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
        window.decorView.setBackgroundColor(bg)
        window.decorView.systemUiVisibility = 0
        matches = JSONArray()
        val cache = getSharedPreferences("zekify_cache", MODE_PRIVATE)
        for ((key, value) in cache.all) {
            if (!key.startsWith("matches_json_") || value !is String) continue
            try {
                val batch = JSONArray(value)
                for (i in 0 until batch.length()) batch.optJSONObject(i)?.let { match ->
                    match.put("_zekify_sport", key.removePrefix("matches_json_"))
                    matches.put(match)
                }
            } catch (_: Exception) { }
        }
        collectFollowedEntities()
        render()
    }

    private fun collectFollowedEntities() {
        val prefs = getSharedPreferences("zekify_favourites", MODE_PRIVATE)
        val teamKeys = prefs.getStringSet("teams", emptySet()) ?: emptySet()
        val competitionKeys = prefs.getStringSet("competitions", emptySet()) ?: emptySet()
        for (key in teamKeys) {
            try { teams[key] = JSONObject(prefs.getString("team_meta_$key", "{}") ?: "{}") } catch (_: Exception) { }
        }
        for (key in competitionKeys) {
            try { competitions[key] = JSONObject(prefs.getString("competition_meta_$key", "{}") ?: "{}") } catch (_: Exception) { }
        }
        for (index in 0 until matches.length()) {
            val match = matches.optJSONObject(index) ?: continue
            val matchTeams = match.optJSONObject("teams")
            for (side in listOf("home", "away")) {
                val team = matchTeams?.optJSONObject(side) ?: match.optJSONObject(side)
                    ?: match.optJSONObject(side + "Team") ?: match.optJSONObject(side + "_team")
                    ?: continue
                val id = first(team, "id", "teamId", "team_id", "providerId", "provider_id")
                val name = first(team, "name", "teamName", "team_name", "displayName")
                val key = id.ifBlank { normalize(name) }
                if (key.isNotBlank() && teamKeys.contains(key)) teams[key] = team
            }
            val competition = match.optJSONObject("competition") ?: match.optJSONObject("leagueData")
                ?: match.optJSONObject("tournament")
            val compId = first(competition, "id", "competitionId", "competition_id", "leagueId", "league_id", "providerId", "provider_id")
                .ifBlank { first(match, "competitionId", "competition_id", "leagueId", "league_id") }
            val compName = first(competition, "name", "title", "displayName", "league")
                .ifBlank { first(match, "league", "competitionName", "competition_name") }
            val compKey = compId.ifBlank { normalize(compName) }
            if (compKey.isNotBlank() && competitionKeys.contains(compKey)) {
                competitions[compKey] = competition ?: JSONObject().put("name", compName).put("id", compKey)
            }
        }
        for (key in teamKeys) {
            if (!teams.containsKey(key)) teams[key] = JSONObject().put("id", key).put("name", key)
        }
        for (key in competitionKeys) {
            if (!competitions.containsKey(key)) competitions[key] = JSONObject().put("id", key).put("name", key)
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = text("‹", 34, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(panel, 14)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("FOLLOWING", 10, orange, true))
        heading.addView(text("Teams & competitions", 19, Color.WHITE, true))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        root.addView(header)

        val search = EditText(this).apply { hint = "Search matches, teams and leagues"; setHintTextColor(0xff777777.toInt()); setTextColor(Color.WHITE); textSize = 14f; isSingleLine = true; setPadding(dp(14), 0, dp(14), 0); background = rounded(Color.rgb(17,17,17), 13); addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} ; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { searchQuery = s?.toString().orEmpty(); refreshList() }; override fun afterTextChanged(s: android.text.Editable?) {} }) }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(panel, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        tabs.addView(tab("Live", "live"), LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(tab("Upcoming", "upcoming"), LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(tab("Teams", "teams"), LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(tab("Leagues", "competitions"), LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        listHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(20))
        }
        scroll.addView(listHost, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshList()
        root.alpha = 1f
        root.translationY = 0f
    }

    private fun tab(title: String, key: String): TextView = text(title, 13, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = rounded(if (activeTab == key) 0xff302016.toInt() else Color.TRANSPARENT, 11)
        setOnClickListener {
            activeTab = key
            render()
        }
    }

    private fun refreshList() {
        listHost.removeAllViews()
        if (activeTab == "live" || activeTab == "upcoming") {
            val prefs = getSharedPreferences("zekify_favourites", MODE_PRIVATE)
            val teamKeys = prefs.getStringSet("teams", emptySet()) ?: emptySet()
            val leagueKeys = prefs.getStringSet("competitions", emptySet()) ?: emptySet()
            val selected = mutableListOf<JSONObject>()
            for (i in 0 until matches.length()) {
                val match = matches.optJSONObject(i) ?: continue
                val status = first(match, "status", "statusText", "state").lowercase(Locale.ROOT)
                val kickoff = match.optLong("date", 0L)
                val live = status.contains("live") || status.contains("in play") || status == "1h" || status == "2h" || status == "ht"
                val finished = status.contains("finished") || status == "ft" || status.contains("ended") || status.contains("full time")
                if (activeTab == "live" && !live) continue
                if (activeTab == "upcoming" && (live || finished || kickoff <= System.currentTimeMillis())) continue
                var followed = false
                val matchTeams = match.optJSONObject("teams")
                for (side in listOf("home", "away")) {
                    val team = matchTeams?.optJSONObject(side) ?: match.optJSONObject(side)
                        ?: match.optJSONObject(side + "Team") ?: match.optJSONObject(side + "_team")
                    val id = first(team, "id", "teamId", "team_id", "providerId", "provider_id")
                    val name = first(team, "name", "teamName", "team_name", "displayName")
                    if (teamKeys.contains(id.ifBlank { normalize(name) })) followed = true
                }
                val competition = match.optJSONObject("competition") ?: match.optJSONObject("leagueData") ?: match.optJSONObject("tournament")
                val compId = first(competition, "id", "competitionId", "competition_id", "leagueId", "league_id", "providerId", "provider_id")
                val compName = first(competition, "name", "title", "displayName", "league").ifBlank { first(match, "league", "competitionName", "competition_name") }
                if (leagueKeys.contains(compId.ifBlank { normalize(compName) })) followed = true
                if (followed) {
                    val homeName = first(matchTeams?.optJSONObject("home") ?: match.optJSONObject("home"), "name", "teamName", "team_name")
                    val awayName = first(matchTeams?.optJSONObject("away") ?: match.optJSONObject("away"), "name", "teamName", "team_name")
                    val searchable = (homeName + " " + awayName + " " + compName + " " + first(match, "league", "competitionName", "competition_name")).lowercase(Locale.ROOT)
                    if (searchQuery.isBlank() || searchable.contains(searchQuery.lowercase(Locale.ROOT))) selected.add(match)
                }
            }
            if (selected.isEmpty()) {
                listHost.addView(text(if (activeTab == "live") "No followed teams are live right now" else "No upcoming followed fixtures", 14, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(28), dp(12), dp(28))
                })
                return
            }
            listHost.addView(text(if (activeTab == "live") "LIVE NOW" else "UPCOMING FIXTURES", 10, Color.WHITE, true).apply { setPadding(0, 0, 0, dp(10)) })
            for (match in selected) {
                val teams = match.optJSONObject("teams")
                val home = teams?.optJSONObject("home") ?: match.optJSONObject("home") ?: match.optJSONObject("homeTeam")
                val away = teams?.optJSONObject("away") ?: match.optJSONObject("away") ?: match.optJSONObject("awayTeam")
                val title = first(home, "name", "teamName").ifBlank { "Home" } + " vs " + first(away, "name", "teamName").ifBlank { "Away" }
                val status = first(match, "status", "statusText", "state").ifBlank { if (activeTab == "live") "LIVE" else "Upcoming" }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(panel, 15)
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    setOnClickListener {
                        val open = android.content.Intent(this@FollowedEntitiesActivity, MainActivity::class.java)
                        open.putExtra("matchId", match.optString("id", ""))
                        open.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(open)
                    }
                }
                val matchup = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                matchup.addView(entityBadge(home, title.substringBefore(" vs ")), LinearLayout.LayoutParams(dp(38), dp(38)))
                val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                names.addView(text(title, 13, Color.WHITE, true))
                names.addView(text(first(match, "league", "competitionName").ifBlank { status }, 10, muted, false).apply { setPadding(0, dp(4), 0, 0) })
                matchup.addView(names, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10); rightMargin = dp(8) })
                matchup.addView(entityBadge(away, title.substringAfter(" vs ")), LinearLayout.LayoutParams(dp(38), dp(38)))
                card.addView(matchup)
                if (activeTab == "live") {
                    val watch = text("▶  Watch Live", 12, Color.WHITE, true).apply {
                        gravity = Gravity.CENTER
                        background = rounded(0xff292929.toInt(), 11)
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        setOnClickListener {
                            val open = android.content.Intent(this@FollowedEntitiesActivity, MainActivity::class.java)
                            open.putExtra("matchId", match.optString("id", ""))
                            open.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(open)
                        }
                    }
                    card.addView(watch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }
                listHost.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            return
        }
        val items = if (activeTab == "teams") teams else competitions
        val filtered = items.filterValues { first(it, "name", "teamName", "team_name", "title").contains(searchQuery, true) }
        if (filtered.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(panel, 18)
                setPadding(dp(20), dp(28), dp(20), dp(28))
            }
            val artId = resources.getIdentifier("zekify_illustration_empty_fixtures", "drawable", packageName)
            if (artId != 0) {
                val art = android.widget.ImageView(this).apply { setImageResource(artId); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
                empty.addView(art, LinearLayout.LayoutParams(dp(132), dp(100)))
            }
            empty.addView(text(if (activeTab == "teams") "No followed teams yet" else "No followed competitions yet", 15, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
            })
            empty.addView(text("Open a team or competition profile and tap Follow to save it here.", 11, muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
            listHost.addView(empty)
        }
        if (filtered.isNotEmpty()) listHost.addView(text(if (activeTab == "teams") "YOUR TEAMS" else "YOUR COMPETITIONS", 10, orange, true).apply {
            setPadding(0, 0, 0, dp(10))
        })
        for ((key, entity) in filtered) {
            val name = first(entity, "name", "teamName", "team_name", "title").ifBlank { key }
            val country = first(entity, "country", "countryName", "country_name")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(panel, 15)
                setPadding(dp(15), dp(14), dp(15), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val intent = android.content.Intent(this@FollowedEntitiesActivity, FootballEntityActivity::class.java)
                    intent.putExtra("entity_type", if (activeTab == "teams") "team" else "competition")
                    intent.putExtra("entity_name", name)
                    intent.putExtra("entity_json", entity.toString())
                    intent.putExtra("sport", "football")
                    startActivity(intent)
                }
            }
            val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            identity.addView(entityBadge(entity, name), LinearLayout.LayoutParams(dp(54), dp(54)))
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            words.addView(text(name, 15, Color.WHITE, true))
            words.addView(text(country.ifBlank { first(entity, "sport").ifBlank { if (activeTab == "teams") "Sports team" else "Competition" } }, 11, muted, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            identity.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(13) })
            identity.addView(text("›", 26, muted, false))
            card.addView(identity)
            listHost.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        val suggested = linkedMapOf<String, JSONObject>()
        for (i in 0 until matches.length()) {
            val match = matches.optJSONObject(i) ?: continue
            if (activeTab == "teams") {
                val sides = match.optJSONObject("teams")
                for (side in listOf("home", "away")) {
                    val team = sides?.optJSONObject(side) ?: match.optJSONObject(side) ?: match.optJSONObject(side + "Team") ?: continue
                    val name = first(team, "name", "teamName", "team_name", "displayName")
                    val key = first(team, "id", "teamId", "team_id", "providerId", "provider_id").ifBlank { normalize(name) }
                    if (name.isNotBlank() && key.isNotBlank() && !teams.containsKey(key) && name.contains(searchQuery, true)) suggested[key] = team
                }
            } else {
                val comp = match.optJSONObject("competition") ?: match.optJSONObject("leagueData") ?: match.optJSONObject("tournament")
                val name = first(comp, "name", "title", "displayName").ifBlank { first(match, "league", "competitionName", "competition_name") }
                val key = first(comp, "id", "competitionId", "competition_id", "leagueId", "league_id").ifBlank { normalize(name) }
                if (name.isNotBlank() && key.isNotBlank() && !competitions.containsKey(key) && name.contains(searchQuery, true)) suggested[key] = comp ?: JSONObject().put("name", name).put("id", key)
            }
            if (suggested.size >= 6) break
        }
        if (suggested.isNotEmpty()) {
            listHost.addView(text("SUGGESTED " + if (activeTab == "teams") "TEAMS" else "LEAGUES", 10, Color.WHITE, true).apply { setPadding(0, dp(18), 0, dp(10)) })
            for ((_, entity) in suggested) {
                val name = first(entity, "name", "teamName", "team_name", "title")
                val card = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(panel, 14)
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    setOnClickListener {
                        val intent = android.content.Intent(this@FollowedEntitiesActivity, FootballEntityActivity::class.java)
                        intent.putExtra("entity_type", if (activeTab == "teams") "team" else "competition")
                        intent.putExtra("entity_name", name)
                        intent.putExtra("entity_json", entity.toString())
                        intent.putExtra("sport", "football")
                        startActivity(intent)
                    }
                }
                card.addView(entityBadge(entity, name), LinearLayout.LayoutParams(dp(40), dp(40)))
                card.addView(text(name, 13, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
                card.addView(text("＋", 20, muted, true))
                listHost.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
    }

    private fun entityBadge(entity: JSONObject?, label: String): ImageView {
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(Color.rgb(34, 34, 34), 50)
            contentDescription = label + " badge"
        }
        val source = first(entity, "badge", "badgeUrl", "badge_url", "logo", "logoUrl", "logo_url", "image", "imageUrl", "image_url", "crest", "crestUrl", "crest_url")
        val bundled = TeamBadgeCatalog.resolveBundledTeamBadge(this, entity).ifBlank { TeamBadgeCatalog.resolveBundledCompetitionBadge(this, entity) }
        if (source.startsWith("http")) {
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(source).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 7000
                    connection.instanceFollowRedirects = true
                    connection.inputStream.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) runOnUiThread { if (!isFinishing) image.setImageBitmap(bitmap) }
                    }
                } catch (_: Exception) {
                } finally { connection?.disconnect() }
            }.start()
        }
        } else if (bundled.startsWith("asset://")) {
            try {
                assets.open(bundled.removePrefix("asset://")).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) image.setImageBitmap(bitmap)
                }
            } catch (_: Exception) { }
        }
        return image
    }

    private fun first(obj: JSONObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            val value = obj.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null" && value != "0") return value
        }
        return ""
    }
    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
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
