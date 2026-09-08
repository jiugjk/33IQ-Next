package com.jiugjk.iq33.feature.favourite.domain.repository

/**
 * Outcome of a local bookmark storage operation.
 *
 * Room throws when the database is corrupt, unwritable or simply cannot be opened. Those exceptions
 * used to escape straight out of a `viewModelScope.launch` and crash the app, so the storage
 * boundary reports them as a value instead - callers decide what to show.
 */
sealed interface BookmarkResult<out T> {
    data class Success<T>(
        val value: T,
    ) : BookmarkResult<T>

    data class Failure(
        val throwable: Throwable,
    ) : BookmarkResult<Nothing>
}
