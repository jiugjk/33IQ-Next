package com.jiugjk.iq33.feature.favourite.domain.usecase

import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow

class ObserveBookmarksUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<SavedQuestion>> = bookmarkRepository.observeBookmarks()
}
