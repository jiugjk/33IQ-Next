package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.repository.FeedbackPreferencesRepository
import com.jiugjk.iq33.feature.settings.domain.repository.QuizFilterPreferencesRepository
import com.jiugjk.iq33.feature.settings.domain.usecase.ObserveThemeModeUseCase
import com.jiugjk.iq33.feature.settings.domain.usecase.SetThemeModeUseCase
import com.jiugjk.iq33.library.network.KnowledgeChangeLog
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    private val sessionManager: SessionManager,
    private val observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val feedbackPreferencesRepository: FeedbackPreferencesRepository,
    private val quizFilterPreferencesRepository: QuizFilterPreferencesRepository,
    private val knowledgeChangeLog: KnowledgeChangeLog,
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
            feedbackPreferencesRepository.animationsEnabled.collect { enabled ->
                sendAction(SettingsAction.AnimationsChanged(enabled))
            }
        }

        viewModelScope.launch {
            feedbackPreferencesRepository.hapticsEnabled.collect { enabled ->
                sendAction(SettingsAction.HapticsChanged(enabled))
            }
        }

        viewModelScope.launch {
            quizFilterPreferencesRepository.hideAnswered.collect { hide ->
                sendAction(SettingsAction.HideAnsweredChanged(hide))
            }
        }

        viewModelScope.launch {
            sessionManager.refreshFromServer()
        }

        viewModelScope.launch {
            knowledgeChangeLog.recent.collect { changes ->
                sendAction(SettingsAction.KnowledgeChangesChanged(changes))
            }
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        setThemeModeUseCase(themeMode)
    }

    fun onAnimationsChanged(enabled: Boolean) {
        feedbackPreferencesRepository.setAnimationsEnabled(enabled)
    }

    fun onHapticsChanged(enabled: Boolean) {
        feedbackPreferencesRepository.setHapticsEnabled(enabled)
    }

    fun onHideAnsweredChanged(hide: Boolean) {
        quizFilterPreferencesRepository.setHideAnswered(hide)
    }

    fun onKnowledgeExpandedChanged(expanded: Boolean) {
        sendAction(SettingsAction.KnowledgeExpandedChanged(expanded))
    }

    fun onLogoutClick() {
        knowledgeChangeLog.clear()
        sessionManager.logout()
    }
}
