package com.jiugjk.iq33.feature.base.domain.result

sealed interface Result<out T> {
    data class Success<T>(
        val value: T,
    ) : Result<T>

    data class Failure(
        val throwable: Throwable? = null,
        /**
         * True when an earlier step of a multi-call flow already succeeded, so a retry may charge
         * the account twice. Presentation must not treat this as "nothing happened".
         */
        val afterSideEffect: Boolean = false,
    ) : Result<Nothing>
}
