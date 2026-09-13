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

    /** This account has no history at all - not "the current filter matched nothing". */
    data object Empty : HistoryUiState

    data class Content(
        /** Records matching [filter] and [query]; may be empty while history itself is not. */
        val records: List<AnswerRecord>,
        val filter: HistoryFilter = HistoryFilter.ALL,
        val query: String = "",
        val confirmClear: Boolean = false,
    ) : HistoryUiState {
        /** Search/filter produced nothing, so the controls must stay reachable to undo it. */
        val isZeroMatch: Boolean get() = records.isEmpty()
    }
}
