package com.driftwood.browser.data

import android.content.Context
import com.driftwood.browser.search.SearchEngine
import com.driftwood.browser.ui.theme.ThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BarPosition { BOTTOM, TOP }

data class AppSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val barPosition: BarPosition = BarPosition.BOTTOM,
    /** When true, the app switches between [lightTheme] and [darkTheme] with the phone. */
    val followSystem: Boolean = false,
    val theme: ThemeId = ThemeId.ROSE_PINE_DAWN,
    val lightTheme: ThemeId = ThemeId.ROSE_PINE_DAWN,
    val darkTheme: ThemeId = ThemeId.ROSE_PINE,
    /** Firefox's built-in tracking protection (separate from uBlock Origin). */
    val trackingProtection: Boolean = true,
)

/** User settings, plus a few internal flags. Stored in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val internal = context.getSharedPreferences("internal", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> get() = _state
    val current: AppSettings get() = _state.value

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putString("search", next.searchEngine.name)
            .putString("bar", next.barPosition.name)
            .putBoolean("follow_system", next.followSystem)
            .putString("theme", next.theme.name)
            .putString("light_theme", next.lightTheme.name)
            .putString("dark_theme", next.darkTheme.name)
            .putBoolean("tracking_protection", next.trackingProtection)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            searchEngine = enumOr(prefs.getString("search", null), d.searchEngine),
            barPosition = enumOr(prefs.getString("bar", null), d.barPosition),
            followSystem = prefs.getBoolean("follow_system", d.followSystem),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            lightTheme = enumOr(prefs.getString("light_theme", null), d.lightTheme),
            darkTheme = enumOr(prefs.getString("dark_theme", null), d.darkTheme),
            trackingProtection = prefs.getBoolean("tracking_protection", d.trackingProtection),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
