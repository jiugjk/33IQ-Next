package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

@Immutable
internal sealed interface SearchUiState : BaseState {
    @Immutable
    data object Idle : SearchUiState

    @Immutable
    data object Loading : SearchUiState

    @Immutable
    data object Error : SearchUiState

    @Immutable
    data object Empty : SearchUiState

    @Immutable
    data class Content(
        val questions: List<QuestionSummary>,
    ) : SearchUiState
}
