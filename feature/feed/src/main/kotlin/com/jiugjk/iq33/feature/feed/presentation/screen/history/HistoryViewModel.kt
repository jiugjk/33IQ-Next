package com.jiugjk.iq33.feature.feed.presentation.screen.history

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
            }.map { all ->
                val filtered =
                    all
                        .filter { record ->
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
                        }.filter { record ->
                            query.isBlank() ||
                                record.title.contains(query, ignoreCase = true) ||
                                record.categoryId.contains(query, ignoreCase = true)
                        }
                filtered
            }.collect { list ->
                sendAction(HistoryAction.RecordsChanged(list, filter, query))
            }
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
        val all = answerRecordRepository.current(account).sortedByDescending { it.updatedAt }
        val filtered =
            all
                .filter { record ->
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
                }.filter { record ->
                    query.isBlank() ||
                        record.title.contains(query, ignoreCase = true) ||
                        record.categoryId.contains(query, ignoreCase = true)
                }
        sendAction(HistoryAction.RecordsChanged(filtered, filter, query))
    }
}

internal sealed interface HistoryAction : BaseAction<HistoryUiState> {
    class RecordsChanged(
        private val records: List<AnswerRecord>,
        private val filter: HistoryFilter,
        private val query: String,
    ) : HistoryAction {
        override fun reduce(state: HistoryUiState): HistoryUiState =
            if (records.isEmpty()) {
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
