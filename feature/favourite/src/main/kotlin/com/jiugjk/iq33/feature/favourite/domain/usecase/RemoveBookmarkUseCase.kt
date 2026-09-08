package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult

/**
 * Removes a bookmark outright. The favourites list uses this rather than a toggle: deleting the same
 * row twice - a double tap, or a row already removed from the detail screen - must not re-add it.
 */
class RemoveBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(id: Long): BookmarkResult<Unit> = bookmarkRepository.removeBookmark(id)
}
