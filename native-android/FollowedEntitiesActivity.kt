package com.v3x.zekify

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
    private val orange = Color.rgb(255, 138, 61)
    private lateinit var listHost: LinearLayout
    private var activeTab = "teams"
    private var matches = JSONArray()
    private var teams = linkedMapOf<String, JSONObject>()
    private var competitions = linkedMapOf<String, JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        matches = OfflineMatchStore.readMatches(this, "football") ?: JSONArray()
        collectFollowedEntities()
        render()
    }

    private fun collectFollowedEntities() {
        val prefs = getSharedPreferences("zekify_favourites", MODE_PRIVATE)
        val teamKeys = prefs.getStringSet("teams", emptySet()) ?: emptySet()
        val competitionKeys = prefs.getStringSet("competitions", emptySet()) ?: emptySet()
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

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(panel, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val teamTab = tab("Teams", "teams")
        val competitionTab = tab("Competitions", "competitions")
        tabs.addView(teamTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(competitionTab, LinearLayout.LayoutParams(0, dp(42), 1f))
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
        root.alpha = 0f
        root.translationY = dp(8).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(220L).start()
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
        val items = if (activeTab == "teams") teams else competitions
        if (items.isEmpty()) {
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
            return
        }
        listHost.addView(text(if (activeTab == "teams") "YOUR TEAMS" else "YOUR COMPETITIONS", 10, orange, true).apply {
            setPadding(0, 0, 0, dp(10))
        })
        for ((key, entity) in items) {
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
            card.addView(text(name, 15, Color.WHITE, true))
            card.addView(text(country.ifBlank { if (activeTab == "teams") "Football team" else "Football competition" }, 11, muted, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            listHost.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
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
