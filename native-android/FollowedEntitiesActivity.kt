package com.v3x.zekify

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Native saved teams and competitions screen. */
class FollowedEntitiesActivity : Activity() {
    private lateinit var list: LinearLayout
    private val prefs by lazy { getSharedPreferences("zekify_favourites", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 7, 7))
            setPadding(dp(18), dp(16), dp(18), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = label("‹", 32, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(25, 25, 25), 14)
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = label("FOLLOWING", 16, Color.WHITE, true)
        title.setPadding(dp(13), 0, 0, 0)
        header.addView(title)
        page.addView(header)
        page.addView(label("Your teams and competitions", 12, Color.LTGRAY, false).apply {
            setPadding(0, dp(7), 0, dp(14))
        })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val records = prefs.all.entries
            .filter { it.key.startsWith("entity_") && it.value is String }
            .mapNotNull { entry ->
                try { JSONObject(entry.value as String).put("_storage_key", entry.key) } catch (_: Exception) { null }
            }
            .sortedBy { it.optString("name").lowercase() }
        if (records.isEmpty()) {
            list.addView(label("No followed teams yet", 15, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(32), dp(18), dp(12))
            })
            list.addView(label("Open a team or competition badge and tap Follow to save it here.", 11, Color.LTGRAY, false).apply {
                gravity = Gravity.CENTER
            })
            return
        }
        records.forEach { record ->
            val type = record.optString("type", "teams")
            val name = record.optString("name", "Football team")
            val country = record.optString("country", if (type == "competitions") "Competition" else "Football")
            val key = record.optString("key")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.rgb(22, 22, 22), 16)
                setPadding(dp(14), dp(13), dp(14), dp(13))
            }
            card.addView(label(name, 14, Color.WHITE, true))
            card.addView(label(country, 10, Color.LTGRAY, false).apply {
                setPadding(0, dp(4), 0, dp(11))
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val open = Button(this).apply {
                text = "Open"
                setOnClickListener {
                    startActivity(Intent(this@FollowedEntitiesActivity, FootballEntityActivity::class.java).apply {
                        putExtra("entity_type", if (type == "competitions") "competition" else "team")
                        putExtra("entity_name", name)
                        putExtra("entity_json", record.optString("entity_json", "{}"))
                        putExtra("sport", record.optString("sport", "football"))
                    })
                }
            }
            val remove = Button(this).apply {
                text = "Unfollow"
                setOnClickListener { removeRecord(type, key, record.optString("_storage_key")); render() }
            }
            actions.addView(open, LinearLayout.LayoutParams(0, dp(44), 1f))
            actions.addView(remove, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            card.alpha = 0f
            card.translationY = dp(7).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }
    }

    private fun removeRecord(type: String, key: String, storageKey: String) {
        val collection = if (type == "competitions") "competitions" else "teams"
        val values = (prefs.getStringSet(collection, emptySet()) ?: emptySet()).toMutableSet()
        values.remove(key)
        prefs.edit().putStringSet(collection, values).remove(storageKey).apply()
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
