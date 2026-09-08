package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult

class ToggleBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(question: SavedQuestion): BookmarkResult<Boolean> = bookmarkRepository.toggleBookmark(question)
}
