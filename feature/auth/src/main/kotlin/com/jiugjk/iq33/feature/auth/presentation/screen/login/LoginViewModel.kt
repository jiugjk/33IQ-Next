package com.jiugjk.iq33.feature.auth.presentation.screen.login

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.library.network.LoginError
import com.jiugjk.iq33.library.network.LoginResult
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LoginViewModel(
    private val sessionManager: SessionManager,
) : BaseViewModel<LoginUiState, LoginAction>(LoginUiState()) {
    private var loginJob: Job? = null

    fun onAccountChange(account: String) {
        sendAction(LoginAction.AccountChanged(account))
    }

    fun onPasswordChange(password: String) {
        sendAction(LoginAction.PasswordChanged(password))
    }

    fun login() {
        val state = uiStateFlow.value

        if (state.isLoading || loginJob?.isActive == true) return

        if (state.account.isBlank() || state.password.isBlank()) {
            sendAction(LoginAction.LoginFailure(LoginFailureReason.MISSING_CREDENTIALS))
            return
        }

        sendAction(LoginAction.LoginStart)

        loginJob =
            viewModelScope.launch {
                when (val result = sessionManager.login(state.account, state.password)) {
                    LoginResult.Success -> sendAction(LoginAction.LoginSuccess)
                    is LoginResult.Failure -> sendAction(result.error.toAction())
                }
            }
    }

    private fun LoginError.toAction(): LoginAction.LoginFailure =
        when (this) {
            LoginError.NetworkUnavailable -> LoginAction.LoginFailure(LoginFailureReason.NETWORK_UNAVAILABLE)
            LoginError.UnknownAccount -> LoginAction.LoginFailure(LoginFailureReason.UNKNOWN_ACCOUNT)
            LoginError.WrongPassword -> LoginAction.LoginFailure(LoginFailureReason.WRONG_PASSWORD)
            LoginError.AccountLocked -> LoginAction.LoginFailure(LoginFailureReason.ACCOUNT_LOCKED)
            LoginError.NotVerified -> LoginAction.LoginFailure(LoginFailureReason.NOT_VERIFIED)
            is LoginError.Unknown -> LoginAction.LoginFailure(LoginFailureReason.UNKNOWN, serverStatus)
        }
}
