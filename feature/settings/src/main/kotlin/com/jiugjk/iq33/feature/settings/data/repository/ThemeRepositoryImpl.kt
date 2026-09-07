package com.jiugjk.iq33.feature.settings.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ThemeRepositoryImpl(
    private val preferences: SharedPreferences,
) : ThemeRepository {
    private val _themeModeFlow = MutableStateFlow(loadThemeMode())
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    override fun setThemeMode(themeMode: ThemeMode) {
        preferences.edit { putString(PREF_KEY_THEME_MODE, themeMode.name) }
        _themeModeFlow.value = themeMode
    }

    private fun loadThemeMode(): ThemeMode {
        val name = preferences.getString(PREF_KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM

        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val PREF_KEY_THEME_MODE = "theme_mode"
    }
}
