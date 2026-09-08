package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.library.network.IqSession

@Immutable
internal sealed interface SettingsUiState : BaseState {
    @Immutable
    data class Content(
        val session: IqSession = IqSession(),
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
    ) : SettingsUiState
}
