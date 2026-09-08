package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult

class IsBookmarkedUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(id: Long): BookmarkResult<Boolean> = bookmarkRepository.isBookmarked(id)
}
