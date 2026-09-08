package com.jiugjk.iq33.feature.favourite.presentation.screen.favourite

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.ObserveBookmarksUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.RemoveBookmarkUseCase
import kotlinx.coroutines.launch

internal class FavouriteViewModel(
    private val observeBookmarksUseCase: ObserveBookmarksUseCase,
    private val removeBookmarkUseCase: RemoveBookmarkUseCase,
) : BaseViewModel<FavouriteUiState, FavouriteAction>(FavouriteUiState.Loading) {
    init {
        viewModelScope.launch {
            observeBookmarksUseCase().collect { result ->
                when (result) {
                    is BookmarkResult.Success -> sendAction(FavouriteAction.BookmarksChanged(result.value))
                    is BookmarkResult.Failure -> sendAction(FavouriteAction.StorageFailed)
                }
            }
        }
    }

    /**
     * Deletes outright instead of toggling: the same row can be tapped twice, or already be gone
     * because the detail screen un-bookmarked it, and a toggle would put it back.
     */
    fun onRemoveClick(savedQuestion: SavedQuestion) {
        viewModelScope.launch {
            if (removeBookmarkUseCase(savedQuestion.id) is BookmarkResult.Failure) {
                sendAction(FavouriteAction.StorageFailed)
            }
        }
    }
}
