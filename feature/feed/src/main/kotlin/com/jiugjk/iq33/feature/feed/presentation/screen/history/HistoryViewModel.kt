package com.jiugjk.iq33.feature.feed.presentation.screen.history

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
internal class HistoryViewModel(
    private val answerRecordRepository: AnswerRecordRepository,
    private val questionProgressRepository: QuestionProgressRepository,
) : BaseViewModel<HistoryUiState, HistoryAction>(HistoryUiState.Loading) {
    private var filter = HistoryFilter.ALL
    private var query = ""

    init {
        viewModelScope.launch {
            combine(answerRecordRepository.records, questionProgressRepository.progress) { records, progressState ->
                val account = progressState.accountKey
                records
                    .filter { it.accountKey == account }
                    .sortedByDescending { it.updatedAt }
            }.collect(::publish)
        }
    }

    fun onFilter(filter: HistoryFilter) {
        this.filter = filter
        refreshFromCache()
    }

    fun onQuery(query: String) {
        this.query = query
        refreshFromCache()
    }

    /** Back to "everything", from the zero-match state as well as from the list. */
    fun onResetFilters() {
        filter = HistoryFilter.ALL
        query = ""
        refreshFromCache()
    }

    fun onDelete(record: AnswerRecord) {
        answerRecordRepository.delete(record.accountKey, record.questionId)
    }

    fun onClearRequested() = sendAction(HistoryAction.ConfirmClear(true))

    fun onClearDismissed() = sendAction(HistoryAction.ConfirmClear(false))

    fun onClearConfirmed() {
        answerRecordRepository.clearAll(questionProgressRepository.current.accountKey)
        sendAction(HistoryAction.ConfirmClear(false))
    }

    private fun refreshFromCache() {
        val account = questionProgressRepository.current.accountKey
        publish(answerRecordRepository.current(account).sortedByDescending { it.updatedAt })
    }

    /**
     * Publishes the filtered list *and* whether the account has any history at all.
     *
     * The two are deliberately separate: "no records" is an empty screen, while "no match" has to
     * keep the search field and the filter chips on screen - otherwise a query that matches nothing
     * takes away the only controls that could undo it.
     */
    private fun publish(all: List<AnswerRecord>) {
        val matching = all.filter { matchesFilter(it) && matchesQuery(it) }
        sendAction(HistoryAction.RecordsChanged(matching, filter, query, all.isNotEmpty()))
    }

    private fun matchesFilter(record: AnswerRecord): Boolean =
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.CORRECT -> record.isCorrect == true
            HistoryFilter.WRONG -> record.isCorrect == false
            HistoryFilter.VIEWED_ONLY ->
                record.viewedExplanation &&
                    record.answeredAt == null &&
                    record.isCorrect == null &&
                    record.selectedOption == null
        }

    /** Question id is searchable too: a record migrated without a title is still findable by number. */
    private fun matchesQuery(record: AnswerRecord): Boolean {
        val term = query.trim()
        return term.isEmpty() ||
            record.title.contains(term, ignoreCase = true) ||
            record.categoryLabel.contains(term, ignoreCase = true) ||
            record.questionId.toString().contains(term)
    }
}

internal sealed interface HistoryAction : BaseAction<HistoryUiState> {
    class RecordsChanged(
        private val records: List<AnswerRecord>,
        private val filter: HistoryFilter,
        private val query: String,
        private val hasAnyRecords: Boolean,
    ) : HistoryAction {
        override fun reduce(state: HistoryUiState): HistoryUiState =
            if (!hasAnyRecords) {
                HistoryUiState.Empty
            } else {
                val confirm = (state as? HistoryUiState.Content)?.confirmClear == true
                HistoryUiState.Content(records, filter, query, confirm)
            }
    }

    class ConfirmClear(
        private val show: Boolean,
    ) : HistoryAction {
        override fun reduce(state: HistoryUiState): HistoryUiState =
            if (state is HistoryUiState.Content) state.copy(confirmClear = show) else state
    }
}
