package com.jiugjk.iq33.feature.auth.presentation.screen.login

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState

/**
 * Why a login attempt failed, as a category rather than a ready-made sentence - the wording lives in
 * `strings.xml` and is resolved by the screen, so the same state is testable and translatable.
 */
internal enum class LoginFailureReason {
    /** The form was submitted with an empty account or password - never sent to the server. */
    MISSING_CREDENTIALS,
    NETWORK_UNAVAILABLE,
    UNKNOWN_ACCOUNT,
    WRONG_PASSWORD,
    ACCOUNT_LOCKED,

    /** 33IQ accepted the request but the session could not be verified afterwards. */
    NOT_VERIFIED,
    UNKNOWN,
}

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
        val reason: LoginFailureReason,
        /** Raw server-reported status for [LoginFailureReason.UNKNOWN], kept for diagnostics. */
        val serverStatus: String? = null,
    ) : LoginUiState
}
