package com.jiugjk.iq33.feature.settings.presentation.screen.aboutlibraries

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState

@Immutable
internal sealed interface AboutLibrariesUiState : BaseState {
    @Immutable
    data object Content : AboutLibrariesUiState
}
