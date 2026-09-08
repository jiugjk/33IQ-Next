package com.jiugjk.iq33.feature.settings.domain.repository

import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ThemeRepository {
    val themeModeFlow: StateFlow<ThemeMode>

    fun setThemeMode(themeMode: ThemeMode)
}
