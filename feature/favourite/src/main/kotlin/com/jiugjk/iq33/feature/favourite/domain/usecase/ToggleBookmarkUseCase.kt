package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository

class ToggleBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(question: SavedQuestion): Boolean = bookmarkRepository.toggleBookmark(question)
}
