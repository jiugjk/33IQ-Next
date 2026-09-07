package com.jiugjk.iq33.feature.settings.domain.usecase

import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.repository.ThemeRepository

class SetThemeModeUseCase(
    private val themeRepository: ThemeRepository,
) {
    operator fun invoke(themeMode: ThemeMode) {
        themeRepository.setThemeMode(themeMode)
    }
}
