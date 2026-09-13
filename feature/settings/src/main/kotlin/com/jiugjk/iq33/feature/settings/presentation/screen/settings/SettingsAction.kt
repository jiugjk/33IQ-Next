package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.KnowledgeChangeEntry

internal sealed interface SettingsAction : BaseAction<SettingsUiState> {
    class SessionChanged(
        private val session: IqSession,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState = (state as? SettingsUiState.Content)?.copy(session = session) ?: state
    }

    class ThemeModeChanged(
        private val themeMode: ThemeMode,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(themeMode = themeMode) ?: state
    }

    class AnimationsChanged(
        private val enabled: Boolean,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(animationsEnabled = enabled) ?: state
    }

    class HapticsChanged(
        private val enabled: Boolean,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(hapticsEnabled = enabled) ?: state
    }

    class HideAnsweredChanged(
        private val hide: Boolean,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(hideAnswered = hide) ?: state
    }

    class KnowledgeChangesChanged(
        private val changes: List<KnowledgeChangeEntry>,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(knowledgeChanges = changes) ?: state
    }

    class KnowledgeExpandedChanged(
        private val expanded: Boolean,
    ) : SettingsAction {
        override fun reduce(state: SettingsUiState): SettingsUiState =
            (state as? SettingsUiState.Content)?.copy(knowledgeExpanded = expanded) ?: state
    }
}
