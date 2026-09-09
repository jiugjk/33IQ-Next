package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

internal class QuestionDetailViewModel(
    private val getQuestionDetailUseCase: GetQuestionDetailUseCase,
    private val isBookmarkedUseCase: IsBookmarkedUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val questionAnswerUseCases: QuestionAnswerUseCases,
) : BaseViewModel<QuestionDetailUiState, QuestionDetailAction>(QuestionDetailUiState.Loading) {
    private var loadJob: Job? = null
    private var submitJob: Job? = null
    private var bookmarkJob: Job? = null
    private var answerRevealJob: Job? = null
    private var hintQuoteJob: Job? = null
    private var hintRevealJob: Job? = null
    private var praiseJob: Job? = null
    private var loadedQuestionId: Long? = null
    private val submitMutex = Mutex()
    private val answerRevealMutex = Mutex()
    private val hintQuoteMutex = Mutex()
    private val hintRevealMutex = Mutex()
    private val praiseMutex = Mutex()
    private val bookmarkMutex = Mutex()

    /**
     * Loads [id], skipping the work when this question is already loaded or still loading.
     *
     * Re-entering composition - a rotation, or returning from another screen - runs the screen's
     * `LaunchedEffect` again. Reloading there would replace a `Content` state that already carries
     * the user's selection, submission result and any revealed (paid for) answer or hint with a
     * blank one, so only [forceReload] - the retry action - starts a fresh load of the same question.
     */
    fun load(
        id: Long,
        forceReload: Boolean = false,
    ) {
        val alreadyLoaded = loadedQuestionId == id && uiStateFlow.value is QuestionDetailUiState.Content
        val stillLoading = loadedQuestionId == id && loadJob?.isActive == true

        if (!forceReload && (alreadyLoaded || stillLoading)) return

        loadJob?.cancel()
        cancelSideWork()
        loadedQuestionId = id

        sendAction(QuestionDetailAction.LoadStart)

        loadJob =
            viewModelScope.launch {
                when (val result = getQuestionDetailUseCase(id)) {
                    is Result.Success -> sendAction(loadSuccessAction(result.value, id))
                    is Result.Failure -> sendAction(QuestionDetailAction.LoadFailure)
                }
            }
    }

    /**
     * Single entry point for the screen's interactions. Each branch re-checks the interaction rules
     * on the current state: a composable's `enabled` flag is presentation, and two taps delivered in
     * the same frame would both pass it.
     */
    fun onEvent(event: QuestionDetailEvent) {
        val content = uiStateFlow.value as? QuestionDetailUiState.Content

        when (event) {
            QuestionDetailEvent.RetryRequested -> loadedQuestionId?.let { id -> load(id, forceReload = true) }
            is QuestionDetailEvent.ChoiceSelected ->
                if (content?.canSelectChoice == true) sendAction(QuestionDetailAction.ChoiceSelected(event.choiceId))
            is QuestionDetailEvent.DraftAnswerChanged ->
                if (content?.canSelectChoice == true) sendAction(QuestionDetailAction.DraftAnswerChanged(event.text))
            is QuestionDetailEvent.AnswerSubmitted -> submitAnswer(content, event.answer)
            QuestionDetailEvent.BookmarkToggled -> toggleBookmark(content)
            QuestionDetailEvent.AnswerRevealRequested -> requestAnswerReveal(content)
            QuestionDetailEvent.AnswerRevealConfirmed -> confirmAnswerReveal(content)
            QuestionDetailEvent.HintQuoteRequested -> requestHintQuote(content)
            QuestionDetailEvent.HintRevealConfirmed -> confirmHintReveal(content)
            QuestionDetailEvent.PraiseClicked -> praise(content)
            is QuestionDetailEvent.RevealFlowDismissed -> sendAction(event.kind.dismissAction())
        }
    }

    /** Submits [answer]. 33IQ itself also rejects a second submission. */
    private fun submitAnswer(
        content: QuestionDetailUiState.Content?,
        answer: String,
    ) {
        if (content?.canSelectChoice != true || answer.isBlank() || submitJob?.isActive == true) return

        val questionId = content.detail.id

        submitJob =
            viewModelScope.launch {
                if (!submitMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (!latest.canSelectChoice || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.SubmissionStarted(questionId, answer))
                    if ((uiStateFlow.value as? QuestionDetailUiState.Content)?.isSubmitting != true) return@launch

                    when (val result = questionAnswerUseCases.submitAnswer(questionId, answer)) {
                        is Result.Success -> sendAction(QuestionDetailAction.SubmissionFinished(questionId, result.value))
                        is Result.Failure -> sendAction(QuestionDetailAction.SubmissionFailed(questionId))
                    }
                } finally {
                    submitMutex.unlock()
                }
            }
    }

    private fun toggleBookmark(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isBookmarkChanging || bookmarkJob?.isActive == true) return

        val questionId = content.detail.id
        val savedQuestion =
            SavedQuestion(
                id = questionId,
                title = content.detail.shortLabel,
                tags = content.detail.tags,
                savedAt = System.currentTimeMillis(),
            )

        bookmarkJob =
            viewModelScope.launch {
                if (!bookmarkMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (latest.isBookmarkChanging || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.BookmarkStarted)

                    when (val result = toggleBookmarkUseCase(savedQuestion)) {
                        is BookmarkResult.Success -> sendAction(QuestionDetailAction.BookmarkChanged(questionId, result.value))
                        is BookmarkResult.Failure -> sendAction(QuestionDetailAction.BookmarkFailed(questionId))
                    }
                } finally {
                    bookmarkMutex.unlock()
                }
            }
    }

    private fun requestAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canStartAnswerReveal != true) return

        sendAction(QuestionDetailAction.AnswerConfirmRequested)
    }

    private fun confirmAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canConfirmAnswerReveal != true || answerRevealJob?.isActive == true) return

        val questionId = content.detail.id

        answerRevealJob =
            viewModelScope.launch {
                if (!answerRevealMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (!latest.canConfirmAnswerReveal || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.AnswerRevealStarted(questionId))
                    if ((uiStateFlow.value as? QuestionDetailUiState.Content)?.answerReveal !is RevealState.Revealing) {
                        return@launch
                    }

                    when (val result = questionAnswerUseCases.revealAnswer(questionId)) {
                        is Result.Success -> sendAction(QuestionDetailAction.AnswerRevealFinished(questionId, result.value))
                        is Result.Failure ->
                            sendAction(QuestionDetailAction.AnswerFlowFailed(questionId, result.afterSideEffect))
                    }
                } finally {
                    answerRevealMutex.unlock()
                }
            }
    }

    /** Fetches 33IQ's own price quote for a hint - it reports the account's actual member-discounted price. */
    private fun requestHintQuote(content: QuestionDetailUiState.Content?) {
        if (content?.canStartHintReveal != true || hintQuoteJob?.isActive == true) return

        val questionId = content.detail.id

        hintQuoteJob =
            viewModelScope.launch {
                if (!hintQuoteMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (!latest.canStartHintReveal || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.HintQuoteStarted(questionId))
                    if ((uiStateFlow.value as? QuestionDetailUiState.Content)?.hintReveal !is RevealState.QuoteLoading) {
                        return@launch
                    }

                    when (val result = questionAnswerUseCases.quoteHint(questionId)) {
                        is Result.Success -> sendAction(QuestionDetailAction.HintQuoteReady(questionId, result.value))
                        is Result.Failure ->
                            sendAction(QuestionDetailAction.HintFlowFailed(questionId, result.afterSideEffect))
                    }
                } finally {
                    hintQuoteMutex.unlock()
                }
            }
    }

    private fun confirmHintReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canConfirmHintReveal != true || hintRevealJob?.isActive == true) return

        val questionId = content.detail.id

        hintRevealJob =
            viewModelScope.launch {
                if (!hintRevealMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (!latest.canConfirmHintReveal || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.HintRevealStarted(questionId))
                    if ((uiStateFlow.value as? QuestionDetailUiState.Content)?.hintReveal !is RevealState.Revealing) {
                        return@launch
                    }

                    when (val result = questionAnswerUseCases.revealHint(questionId)) {
                        is Result.Success -> sendAction(QuestionDetailAction.HintRevealFinished(questionId, result.value))
                        is Result.Failure ->
                            sendAction(QuestionDetailAction.HintFlowFailed(questionId, result.afterSideEffect))
                    }
                } finally {
                    hintRevealMutex.unlock()
                }
            }
    }

    private fun praise(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isPraising || praiseJob?.isActive == true) return

        val questionId = content.detail.id

        praiseJob =
            viewModelScope.launch {
                if (!praiseMutex.tryLock()) return@launch
                try {
                    val latest = uiStateFlow.value as? QuestionDetailUiState.Content ?: return@launch
                    if (latest.isPraising || latest.detail.id != questionId) return@launch

                    sendAction(QuestionDetailAction.PraiseStarted)

                    when (val result = questionAnswerUseCases.praiseQuestion(questionId)) {
                        is Result.Success -> sendAction(QuestionDetailAction.Praised(questionId, result.value))
                        is Result.Failure -> sendAction(QuestionDetailAction.PraiseFailed(questionId))
                    }
                } finally {
                    praiseMutex.unlock()
                }
            }
    }

    private fun cancelSideWork() {
        submitJob?.cancel()
        bookmarkJob?.cancel()
        answerRevealJob?.cancel()
        hintQuoteJob?.cancel()
        hintRevealJob?.cancel()
        praiseJob?.cancel()
    }

    /** A failed bookmark lookup must not take the loaded question down with it - it is a side note. */
    private suspend fun loadSuccessAction(
        detail: QuestionDetail,
        id: Long,
    ): QuestionDetailAction.LoadSuccess =
        when (val bookmarked = isBookmarkedUseCase(id)) {
            is BookmarkResult.Success -> QuestionDetailAction.LoadSuccess(detail, bookmarked.value)
            is BookmarkResult.Failure -> QuestionDetailAction.LoadSuccess(detail, isBookmarked = false, bookmarkFailed = true)
        }
}
