package com.jiugjk.iq33.feature.favourite.domain.repository

import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import kotlinx.coroutines.flow.Flow

/**
 * 33IQ has no confirmed/working "collect" (收藏) endpoint reachable without an authenticated,
 * decompiled-app-verified request, so favourites are kept locally on-device instead: this is a
 * fully working bookmark list rather than a stub that silently fails against a guessed API.
 *
 * Every operation reports storage failures instead of letting a SQLite exception escape into the
 * caller's coroutine, where nothing would catch it.
 */
interface BookmarkRepository {
    /** Emits the current bookmarks; a storage failure is emitted as [BookmarkResult.Failure], not thrown. */
    fun observeBookmarks(): Flow<BookmarkResult<List<SavedQuestion>>>

    suspend fun isBookmarked(id: Long): BookmarkResult<Boolean>

    /** Adds or removes [question] from bookmarks in one transaction. Returns the new bookmarked state. */
    suspend fun toggleBookmark(question: SavedQuestion): BookmarkResult<Boolean>

    /** Removes [id] if present. Idempotent - unlike a toggle, a repeated remove cannot re-add it. */
    suspend fun removeBookmark(id: Long): BookmarkResult<Unit>
}
