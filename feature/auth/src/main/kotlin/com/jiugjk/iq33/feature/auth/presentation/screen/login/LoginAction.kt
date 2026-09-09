package com.jiugjk.iq33.feature.auth.presentation.screen.login

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction

internal sealed interface LoginAction : BaseAction<LoginUiState> {
    class AccountChanged(
        private val account: String,
    ) : LoginAction {
        override fun reduce(state: LoginUiState) = state.copy(account = account, failureReason = null, serverStatus = null)
    }

    class PasswordChanged(
        private val password: String,
    ) : LoginAction {
        override fun reduce(state: LoginUiState) = state.copy(password = password, failureReason = null, serverStatus = null)
    }

    object LoginStart : LoginAction {
        override fun reduce(state: LoginUiState): LoginUiState =
            if (state.isLoading) state else state.copy(isLoading = true, isSuccess = false, failureReason = null, serverStatus = null)
    }

    object LoginSuccess : LoginAction {
        override fun reduce(state: LoginUiState) = state.copy(isLoading = false, isSuccess = true, failureReason = null)
    }

    class LoginFailure(
        private val reason: LoginFailureReason,
        private val serverStatus: String? = null,
    ) : LoginAction {
        override fun reduce(state: LoginUiState) =
            state.copy(isLoading = false, isSuccess = false, failureReason = reason, serverStatus = serverStatus)
    }
}
