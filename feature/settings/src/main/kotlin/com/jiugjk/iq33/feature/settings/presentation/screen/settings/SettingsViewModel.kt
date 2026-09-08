package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.usecase.ObserveThemeModeUseCase
import com.jiugjk.iq33.feature.settings.domain.usecase.SetThemeModeUseCase
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    private val sessionManager: SessionManager,
    private val observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
) : BaseViewModel<SettingsUiState, SettingsAction>(SettingsUiState.Content()) {
    init {
        viewModelScope.launch {
            sessionManager.sessionFlow.collect { session ->
                sendAction(SettingsAction.SessionChanged(session))
            }
        }

        viewModelScope.launch {
            observeThemeModeUseCase().collect { themeMode ->
                sendAction(SettingsAction.ThemeModeChanged(themeMode))
            }
        }

        viewModelScope.launch {
            sessionManager.refreshFromServer()
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        setThemeModeUseCase(themeMode)
    }

    fun onLogoutClick() {
        sessionManager.logout()
    }
}
