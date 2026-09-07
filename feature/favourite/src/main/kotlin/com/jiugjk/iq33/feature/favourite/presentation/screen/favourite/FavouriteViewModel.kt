package com.jiugjk.iq33.feature.favourite.presentation.screen.favourite

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.usecase.ObserveBookmarksUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import kotlinx.coroutines.launch

internal class FavouriteViewModel(
    private val observeBookmarksUseCase: ObserveBookmarksUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
) : BaseViewModel<FavouriteUiState, FavouriteAction>(FavouriteUiState.Loading) {
    init {
        viewModelScope.launch {
            observeBookmarksUseCase().collect { savedQuestions ->
                sendAction(FavouriteAction.BookmarksChanged(savedQuestions))
            }
        }
    }

    fun onRemoveClick(savedQuestion: SavedQuestion) {
        viewModelScope.launch {
            toggleBookmarkUseCase(savedQuestion)
        }
    }
}
