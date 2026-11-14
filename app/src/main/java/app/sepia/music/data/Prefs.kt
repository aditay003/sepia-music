package app.sepia.music.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("sepia", Context.MODE_PRIVATE)

    /** "high" or "low" */
    var quality: String
        get() = sp.getString("quality", "high") ?: "high"
        set(v) { sp.edit().putString("quality", v).apply() }

    var wifiOnlyDownloads: Boolean
        get() = sp.getBoolean("wifi_only", false)
        set(v) { sp.edit().putBoolean("wifi_only", v).apply() }

    /** Optional Piped-compatible server used as a fallback for stream URLs. */
    var pipedUrl: String
        get() = sp.getString("piped_url", "") ?: ""
        set(v) { sp.edit().putString("piped_url", v.trim()).apply() }

    /** Keep playing similar songs when the queue runs out. */
    var autoplay: Boolean
        get() = sp.getBoolean("autoplay", true)
        set(v) { sp.edit().putBoolean("autoplay", v).apply() }

    /** "default", "apple", "wavy", "ytm" or "spotify" */
    var playerStyle: String
        get() = sp.getString("player_style", "default") ?: "default"
        set(v) { sp.edit().putString("player_style", v).apply() }

    /** "system", "light" or "dark" */
    var themeMode: String
        get() = sp.getString("theme_mode", "system") ?: "system"
        set(v) { sp.edit().putString("theme_mode", v).apply() }

    var cacheMb: Int
        get() = sp.getInt("cache_mb", 512)
        set(v) { sp.edit().putInt("cache_mb", v).apply() }

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) { sp.edit().putBoolean("asked_notif", v).apply() }

    /** Last queue: JSON {"ids":[...],"index":0,"pos":0} */
    var lastSession: String
        get() = sp.getString("last_session", "") ?: ""
        set(v) { sp.edit().putString("last_session", v).apply() }
}
