package com.jiugjk.iq33.feature.auth.presentation.screen.login

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction

internal sealed interface LoginAction : BaseAction<LoginUiState> {
    object LoginStart : LoginAction {
        override fun reduce(state: LoginUiState) = LoginUiState.Loading
    }

    object LoginSuccess : LoginAction {
        override fun reduce(state: LoginUiState) = LoginUiState.Success
    }

    class LoginFailure(
        private val reason: LoginFailureReason,
        private val serverStatus: String? = null,
    ) : LoginAction {
        override fun reduce(state: LoginUiState) = LoginUiState.Failure(reason, serverStatus)
    }
}
