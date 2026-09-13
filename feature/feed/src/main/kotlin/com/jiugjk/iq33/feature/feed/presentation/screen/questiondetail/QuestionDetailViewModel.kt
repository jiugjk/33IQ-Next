package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.AnswerRevealUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal class QuestionDetailViewModel(
    private val getQuestionDetailUseCase: GetQuestionDetailUseCase,
    private val isBookmarkedUseCase: IsBookmarkedUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val questionAnswerUseCases: QuestionAnswerUseCases,
    private val questionProgressRepository: QuestionProgressRepository,
    private val answerRevealUseCases: AnswerRevealUseCases,
) : BaseViewModel<QuestionDetailUiState, QuestionDetailAction>(QuestionDetailUiState.Loading) {
    private var loadJob: Job? = null
    private var loadedQuestionId: Long? = null
    private var accountKey = questionProgressRepository.current.accountKey

    private val currentState: () -> QuestionDetailUiState = { uiStateFlow.value }

    private val submission = SideWorkSlot(viewModelScope, currentState)
    private val bookmarking = SideWorkSlot(viewModelScope, currentState)
    private val hintQuoting = SideWorkSlot(viewModelScope, currentState)
    private val hintRevealing = SideWorkSlot(viewModelScope, currentState)
    private val praising = SideWorkSlot(viewModelScope, currentState)

    private val answerReveal =
        AnswerRevealFlow(
            scope = viewModelScope,
            currentState = currentState,
            answerRevealUseCases = answerRevealUseCases,
            questionProgressRepository = questionProgressRepository,
            sendAction = ::sendAction,
        )

    /** Everything a fresh load has to abandon - all of it belongs to the question being replaced. */
    private val sideWork = listOf(submission, bookmarking, hintQuoting, hintRevealing, praising) + answerReveal.slots

    init {
        viewModelScope.launch {
            questionProgressRepository.progress.collect { progress ->
                val changed = accountKey != progress.accountKey
                accountKey = progress.accountKey
                loadedQuestionId?.let { id ->
                    if (changed) {
                        load(id, forceReload = true)
                    } else {
                        sendAction(
                            QuestionDetailAction.AnsweredChanged(
                                id,
                                id in progress.answeredIds,
                                id in progress.viewedAnswerIds,
                                id in progress.pendingAnswerRevealIds,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads [id], skipping the work when this question is already loaded or still loading.
     *
     * Re-entering composition - a rotation, or returning from another screen - runs the screen's
     * `LaunchedEffect` again. Reloading there would replace a `Content` state that already carries
     * the user's selection, submission result and any revealed (paid for) hint with a
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
        val owner = questionProgressRepository.current.accountKey
        accountKey = owner

        sendAction(QuestionDetailAction.LoadStart)

        loadJob =
            viewModelScope.launch {
                val action =
                    when (val result = getQuestionDetailUseCase(id)) {
                        is Result.Success -> loadSuccessAction(result.value, id)
                        is Result.Failure -> QuestionDetailAction.LoadFailure
                    }
                coroutineContext.ensureActive()
                if (owner == questionProgressRepository.current.accountKey) sendAction(action)
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
        if (accountKey != questionProgressRepository.current.accountKey) {
            loadedQuestionId?.let { load(it, forceReload = true) }
            return
        }
        val content = uiStateFlow.value as? QuestionDetailUiState.Content

        when (event) {
            is QuestionDetailEvent.ChoiceSelected -> sendAction(QuestionDetailAction.ChoiceSelected(event.choiceId))
            is QuestionDetailEvent.DraftAnswerChanged -> sendAction(QuestionDetailAction.DraftAnswerChanged(event.text))
            is QuestionDetailEvent.AnswerSubmitted -> submitAnswer(content, event.answer)
            is QuestionDetailEvent.CandidateToggled -> sendAction(QuestionDetailAction.CandidateToggled(event.index))
            else -> onSimpleEvent(event, content)
        }
    }

    /** The parameterless events, split out so [onEvent] stays within one screen of branching. */
    private fun onSimpleEvent(
        event: QuestionDetailEvent,
        content: QuestionDetailUiState.Content?,
    ) {
        when (event) {
            QuestionDetailEvent.RetryRequested -> loadedQuestionId?.let { id -> load(id, forceReload = true) }
            QuestionDetailEvent.CandidatesCleared -> sendAction(QuestionDetailAction.CandidatesCleared)
            QuestionDetailEvent.BookmarkToggled -> toggleBookmark(content)
            QuestionDetailEvent.AnswerQuoteRequested -> answerReveal.requestQuote(content)
            QuestionDetailEvent.AnswerRevealConfirmed -> answerReveal.reveal(content, recovery = false)
            QuestionDetailEvent.AnswerRevealRecovered -> answerReveal.reveal(content, recovery = true)
            QuestionDetailEvent.AnswerFlowDismissed -> sendAction(AnswerRevealAction.Dismissed)
            QuestionDetailEvent.HintQuoteRequested -> requestHintQuote(content)
            QuestionDetailEvent.HintRevealConfirmed -> confirmHintReveal(content)
            QuestionDetailEvent.PraiseClicked -> praise(content)
            QuestionDetailEvent.HintFlowDismissed -> sendAction(QuestionDetailAction.HintFlowDismissed)
            else -> Unit
        }
    }

    /** Submits [answer]. 33IQ itself also rejects a second submission. */
    private fun submitAnswer(
        content: QuestionDetailUiState.Content?,
        answer: String,
    ) {
        if (content?.canSubmitAnswer(answer) != true || submission.isActive) return

        val questionId = content.detail.id
        val progress = questionProgressRepository.current
        if (progress.isSubmissionBlocked(questionId)) {
            sendAction(
                QuestionDetailAction.AnsweredChanged(
                    questionId,
                    questionId in progress.answeredIds,
                    questionId in progress.viewedAnswerIds,
                    questionId in progress.pendingAnswerRevealIds,
                ),
            )
            return
        }

        submission.start(questionId, { it.canSubmitAnswer(answer) }) {
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
