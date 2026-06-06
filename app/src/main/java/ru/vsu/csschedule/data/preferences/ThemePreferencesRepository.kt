package ru.vsu.csschedule.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferencesRepository(
    context: Context,
) {
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isDarkThemeFlow = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_DARK_THEME, false)
    )

    val isDarkTheme: StateFlow<Boolean> = isDarkThemeFlow.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        if (isDarkThemeFlow.value == enabled) return

        sharedPreferences.edit()
            .putBoolean(KEY_DARK_THEME, enabled)
            .apply()

        isDarkThemeFlow.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "ui_preferences"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
