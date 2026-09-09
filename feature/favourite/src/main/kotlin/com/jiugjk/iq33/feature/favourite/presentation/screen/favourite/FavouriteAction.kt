package com.jiugjk.iq33.feature.favourite.presentation.screen.favourite

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion

internal sealed interface FavouriteAction : BaseAction<FavouriteUiState> {
    class BookmarksChanged(
        private val savedQuestions: List<SavedQuestion>,
    ) : FavouriteAction {
        override fun reduce(state: FavouriteUiState): FavouriteUiState =
            if (savedQuestions.isEmpty()) {
                FavouriteUiState.Empty
            } else {
                FavouriteUiState.Content(savedQuestions)
            }
    }

    object StorageFailed : FavouriteAction {
        override fun reduce(state: FavouriteUiState): FavouriteUiState =
            if (state is FavouriteUiState.Content) state.copy(actionFailed = true) else FavouriteUiState.Error
    }
}
