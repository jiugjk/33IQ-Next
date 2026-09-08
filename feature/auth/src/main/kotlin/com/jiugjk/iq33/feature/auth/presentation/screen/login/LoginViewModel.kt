package com.jiugjk.iq33.feature.auth.presentation.screen.login

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.library.network.LoginResult
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.launch

internal class LoginViewModel(
    private val sessionManager: SessionManager,
) : BaseViewModel<LoginUiState, LoginAction>(LoginUiState.Idle) {
    fun login(
        account: String,
        password: String,
    ) {
        if (account.isBlank() || password.isBlank()) {
            sendAction(LoginAction.LoginFailure("请输入账号和密码"))
            return
        }

        sendAction(LoginAction.LoginStart)

        viewModelScope.launch {
            when (val result = sessionManager.login(account, password)) {
                LoginResult.Success -> sendAction(LoginAction.LoginSuccess)
                is LoginResult.Failure -> sendAction(LoginAction.LoginFailure(result.message))
            }
        }
    }
}
