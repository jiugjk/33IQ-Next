package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository

class IsBookmarkedUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(id: Long): Boolean = bookmarkRepository.isBookmarked(id)
}
