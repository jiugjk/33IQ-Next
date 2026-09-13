package com.jiugjk.iq33.feature.feed.presentation.screen.history

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord

internal enum class HistoryFilter {
    ALL,
    CORRECT,
    WRONG,
    VIEWED_ONLY,
}

@Immutable
internal sealed interface HistoryUiState : BaseState {
    data object Loading : HistoryUiState

    data object Empty : HistoryUiState

    data class Content(
        val records: List<AnswerRecord>,
        val filter: HistoryFilter = HistoryFilter.ALL,
        val query: String = "",
        val confirmClear: Boolean = false,
    ) : HistoryUiState
}
