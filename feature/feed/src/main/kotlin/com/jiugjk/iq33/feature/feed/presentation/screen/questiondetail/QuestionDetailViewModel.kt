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
            is QuestionDetailEvent.AnswerSubmitted -> submitAnswer(content, event.choiceId)
            QuestionDetailEvent.BookmarkToggled -> toggleBookmark(content)
            QuestionDetailEvent.AnswerRevealRequested -> requestAnswerReveal(content)
            QuestionDetailEvent.AnswerRevealConfirmed -> confirmAnswerReveal(content)
            QuestionDetailEvent.HintQuoteRequested -> requestHintQuote(content)
            QuestionDetailEvent.HintRevealConfirmed -> confirmHintReveal(content)
            QuestionDetailEvent.PraiseClicked -> praise(content)
            is QuestionDetailEvent.RevealFlowDismissed -> sendAction(event.kind.dismissAction())
        }
    }

    /** Submits [choiceId] as the answer. 33IQ itself also rejects a second submission. */
    private fun submitAnswer(
        content: QuestionDetailUiState.Content?,
        choiceId: String,
    ) {
        if (content?.canSelectChoice != true) return

        val questionId = content.detail.id

        sendAction(QuestionDetailAction.SubmissionStarted(choiceId))

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.submitAnswer(questionId, choiceId)) {
                is Result.Success -> sendAction(QuestionDetailAction.SubmissionFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.SubmissionFailed)
            }
        }
    }

    private fun toggleBookmark(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isBookmarkChanging) return

        val savedQuestion =
            SavedQuestion(
                id = content.detail.id,
                title = content.detail.title,
                tags = content.detail.tags,
                savedAt = System.currentTimeMillis(),
            )

        sendAction(QuestionDetailAction.BookmarkStarted)

        viewModelScope.launch {
            when (val result = toggleBookmarkUseCase(savedQuestion)) {
                is BookmarkResult.Success -> sendAction(QuestionDetailAction.BookmarkChanged(result.value))
                is BookmarkResult.Failure -> sendAction(QuestionDetailAction.BookmarkFailed)
            }
        }
    }

    private fun requestAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canReveal != true || content.isAnswerRevealed) return

        sendAction(QuestionDetailAction.AnswerConfirmRequested)
    }

    private fun confirmAnswerReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canReveal != true) return

        val questionId = content.detail.id

        sendAction(QuestionDetailAction.AnswerRevealStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.revealAnswer(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.AnswerRevealFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.AnswerFlowFailed)
            }
        }
    }

    /** Fetches 33IQ's own price quote for a hint - it reports the account's actual member-discounted price. */
    private fun requestHintQuote(content: QuestionDetailUiState.Content?) {
        if (content?.canReveal != true) return

        val questionId = content.detail.id

        sendAction(QuestionDetailAction.HintQuoteStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.quoteHint(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.HintQuoteReady(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed)
            }
        }
    }

    private fun confirmHintReveal(content: QuestionDetailUiState.Content?) {
        if (content?.canReveal != true) return

        val questionId = content.detail.id

        sendAction(QuestionDetailAction.HintRevealStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.revealHint(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.HintRevealFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed)
            }
        }
    }

    private fun praise(content: QuestionDetailUiState.Content?) {
        if (content == null || content.isPraising) return

        val questionId = content.detail.id

        sendAction(QuestionDetailAction.PraiseStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.praiseQuestion(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.Praised(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.PraiseFailed)
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
