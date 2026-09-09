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

internal class QuestionDetailViewModel(
    private val getQuestionDetailUseCase: GetQuestionDetailUseCase,
    private val isBookmarkedUseCase: IsBookmarkedUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val questionAnswerUseCases: QuestionAnswerUseCases,
) : BaseViewModel<QuestionDetailUiState, QuestionDetailAction>(QuestionDetailUiState.Loading) {
    private var loadJob: Job? = null
    private var loadedQuestionId: Long? = null

    private val currentState: () -> QuestionDetailUiState = { uiStateFlow.value }

    private val submission = SideWorkSlot(viewModelScope, currentState)
    private val bookmarking = SideWorkSlot(viewModelScope, currentState)
    private val answerRevealing = SideWorkSlot(viewModelScope, currentState)
    private val hintQuoting = SideWorkSlot(viewModelScope, currentState)
    private val hintRevealing = SideWorkSlot(viewModelScope, currentState)
    private val praising = SideWorkSlot(viewModelScope, currentState)

    /** Everything a fresh load has to abandon - all of it belongs to the question being replaced. */
    private val sideWork = listOf(submission, bookmarking, answerRevealing, hintQuoting, hintRevealing, praising)

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
        sideWork.forEach { it.cancel() }
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
     *
     * The two draft edits need no check here - their reducers already refuse a state that has moved
     * past `canSelectChoice`, which is the same rule applied where the state lives.
     */
    fun onEvent(event: QuestionDetailEvent) {
        val content = uiStateFlow.value as? QuestionDetailUiState.Content

        when (event) {
            QuestionDetailEvent.RetryRequested -> loadedQuestionId?.let { id -> load(id, forceReload = true) }
            is QuestionDetailEvent.ChoiceSelected -> sendAction(QuestionDetailAction.ChoiceSelected(event.choiceId))
            is QuestionDetailEvent.DraftAnswerChanged -> sendAction(QuestionDetailAction.DraftAnswerChanged(event.text))
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
        if (content?.canSelectChoice != true || answer.isBlank() || submission.isActive) return

        val questionId = content.detail.id

        submission.start(questionId, { it.canSelectChoice }) {
            sendAction(QuestionDetailAction.SubmissionStarted(questionId, answer))

            // The reducer is what decides whether the submission really started; if it refused, no
            // request may be sent.
            if (submission.isEligible(questionId) { it.isSubmitting }) {
                when (val result = questionAnswerUseCases.submitAnswer(questionId, answer)) {
                    is Result.Success -> sendAction(QuestionDetailAction.SubmissionFinished(questionId, result.value))
                    is Result.Failure -> sendAction(QuestionDetailAction.SubmissionFailed(questionId))
                }
            }
        }
    }

    private fun toggleBookmark(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isBookmarkChanging || bookmarking.isActive) return

        val questionId = content.detail.id
        val savedQuestion =
            SavedQuestion(
                id = questionId,
                title = content.detail.shortLabel,
                tags = content.detail.tags,
                savedAt = System.currentTimeMillis(),
            )

        bookmarking.start(questionId, { !it.isBookmarkChanging }) {
            sendAction(QuestionDetailAction.BookmarkStarted)

            when (val result = toggleBookmarkUseCase(savedQuestion)) {
                is BookmarkResult.Success -> sendAction(QuestionDetailAction.BookmarkChanged(questionId, result.value))
                is BookmarkResult.Failure -> sendAction(QuestionDetailAction.BookmarkFailed(questionId))
            }
        }
    }

    private fun requestAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canStartAnswerReveal != true) return

        sendAction(QuestionDetailAction.AnswerConfirmRequested)
    }

    private fun confirmAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canConfirmAnswerReveal != true || answerRevealing.isActive) return

        val questionId = content.detail.id

        answerRevealing.start(questionId, { it.canConfirmAnswerReveal }) {
            sendAction(QuestionDetailAction.AnswerRevealStarted(questionId))

            if (answerRevealing.isEligible(questionId) { it.answerReveal is RevealState.Revealing }) {
                when (val result = questionAnswerUseCases.revealAnswer(questionId)) {
                    is Result.Success -> sendAction(QuestionDetailAction.AnswerRevealFinished(questionId, result.value))
                    is Result.Failure -> sendAction(QuestionDetailAction.AnswerFlowFailed(questionId, result.afterSideEffect))
                }
            }
        }
    }

    /** Fetches 33IQ's own price quote for a hint - it reports the account's actual member-discounted price. */
    private fun requestHintQuote(content: QuestionDetailUiState.Content?) {
        if (content?.canStartHintReveal != true || hintQuoting.isActive) return

        val questionId = content.detail.id

        hintQuoting.start(questionId, { it.canStartHintReveal }) {
            sendAction(QuestionDetailAction.HintQuoteStarted(questionId))

            if (hintQuoting.isEligible(questionId) { it.hintReveal is RevealState.QuoteLoading }) {
                when (val result = questionAnswerUseCases.quoteHint(questionId)) {
                    is Result.Success -> sendAction(QuestionDetailAction.HintQuoteReady(questionId, result.value))
                    is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed(questionId, result.afterSideEffect))
                }
            }
        }
    }

    private fun confirmHintReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canConfirmHintReveal != true || hintRevealing.isActive) return

        val questionId = content.detail.id

        hintRevealing.start(questionId, { it.canConfirmHintReveal }) {
            sendAction(QuestionDetailAction.HintRevealStarted(questionId))

            if (hintRevealing.isEligible(questionId) { it.hintReveal is RevealState.Revealing }) {
                when (val result = questionAnswerUseCases.revealHint(questionId)) {
                    is Result.Success -> sendAction(QuestionDetailAction.HintRevealFinished(questionId, result.value))
                    is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed(questionId, result.afterSideEffect))
                }
            }
        }
    }

    private fun praise(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isPraising || praising.isActive) return

        val questionId = content.detail.id

        praising.start(questionId, { !it.isPraising }) {
            sendAction(QuestionDetailAction.PraiseStarted)

            when (val result = questionAnswerUseCases.praiseQuestion(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.Praised(questionId, result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.PraiseFailed(questionId))
            }
        }
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
