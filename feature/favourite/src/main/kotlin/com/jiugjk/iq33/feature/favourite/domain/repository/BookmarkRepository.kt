package com.jiugjk.iq33.feature.favourite.domain.repository

import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import kotlinx.coroutines.flow.Flow

/**
 * 33IQ has no confirmed/working "collect" (收藏) endpoint reachable without an authenticated,
 * decompiled-app-verified request, so favourites are kept locally on-device instead: this is a
 * fully working bookmark list rather than a stub that silently fails against a guessed API.
 */
interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<SavedQuestion>>

    suspend fun isBookmarked(id: Long): Boolean

    /** Adds or removes [question] from bookmarks. Returns the new bookmarked state. */
    suspend fun toggleBookmark(question: SavedQuestion): Boolean
}
