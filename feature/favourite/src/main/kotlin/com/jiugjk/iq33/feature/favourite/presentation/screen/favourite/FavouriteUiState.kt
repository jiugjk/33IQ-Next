package com.jiugjk.iq33.feature.favourite.presentation.screen.favourite

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion

@Immutable
internal sealed interface FavouriteUiState : BaseState {
    @Immutable
    data object Loading : FavouriteUiState

    @Immutable
    data object Empty : FavouriteUiState

    @Immutable
    data class Content(
        val savedQuestions: List<SavedQuestion>,
    ) : FavouriteUiState
}
