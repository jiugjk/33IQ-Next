package com.jiugjk.iq33.feature.auth.presentation.screen.login

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState

@Immutable
internal sealed interface LoginUiState : BaseState {
    @Immutable
    data object Idle : LoginUiState

    @Immutable
    data object Loading : LoginUiState

    @Immutable
    data object Success : LoginUiState

    @Immutable
    data class Failure(
        val message: String,
    ) : LoginUiState
}
