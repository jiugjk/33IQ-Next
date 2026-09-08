package com.jiugjk.iq33.feature.base.domain.result

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Runs [block] at a repository boundary and turns its outcome into a [Result].
 *
 * Coroutine cancellation is rethrown rather than folded into [Result.Failure]. A cancelled call has
 * no outcome to report: reporting one lets a request that was already superseded - an abandoned
 * search, a category the user switched away from - overwrite the state of the request that replaced
 * it. Every other failure is logged under [logTag] with [failureMessage] and returned.
 */
suspend fun <T> resultOf(
    logTag: String,
    failureMessage: String,
    block: suspend () -> T,
): Result<T> =
    runCatching { block() }
        .fold(
            onSuccess = { value -> Result.Success(value) },
            onFailure = { throwable ->
                if (throwable is CancellationException) throw throwable

                Timber.tag(logTag).w(throwable, failureMessage)
                Result.Failure(throwable)
            },
        )
