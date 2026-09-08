package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.library.network.IqSession

internal sealed interface SettingsAction : BaseAction<SettingsUiState> {
    class SessionChanged(
        private val session: IqSession,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState = (state as SettingsUiState.Content).copy(session = session)
    }

    class ThemeModeChanged(
        private val themeMode: ThemeMode,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState = (state as SettingsUiState.Content).copy(themeMode = themeMode)
    }
}
