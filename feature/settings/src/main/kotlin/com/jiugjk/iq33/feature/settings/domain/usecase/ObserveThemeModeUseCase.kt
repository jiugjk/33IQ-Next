package com.jiugjk.iq33.feature.settings.domain.usecase

import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveThemeModeUseCase(
    private val themeRepository: ThemeRepository,
) {
    operator fun invoke(): StateFlow<ThemeMode> = themeRepository.themeModeFlow
}
