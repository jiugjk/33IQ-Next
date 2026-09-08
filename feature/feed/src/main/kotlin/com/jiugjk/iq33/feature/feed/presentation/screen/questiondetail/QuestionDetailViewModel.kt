package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import kotlinx.coroutines.launch

internal class QuestionDetailViewModel(
    private val getQuestionDetailUseCase: GetQuestionDetailUseCase,
    private val isBookmarkedUseCase: IsBookmarkedUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val questionAnswerUseCases: QuestionAnswerUseCases,
) : BaseViewModel<QuestionDetailUiState, QuestionDetailAction>(QuestionDetailUiState.Loading) {
    fun load(id: Long) {
        sendAction(QuestionDetailAction.LoadStart)

        viewModelScope.launch {
            when (val result = getQuestionDetailUseCase(id)) {
                is Result.Success -> {
                    val isBookmarked = isBookmarkedUseCase(id)
                    sendAction(QuestionDetailAction.LoadSuccess(result.value, isBookmarked))
                }
                is Result.Failure -> {
                    sendAction(QuestionDetailAction.LoadFailure)
                }
            }
        }
    }

    fun onChoiceSelected(choiceId: String) {
        sendAction(QuestionDetailAction.ChoiceSelected(choiceId))
    }

    fun onBookmarkClick(detail: QuestionDetail) {
        viewModelScope.launch {
            val savedQuestion =
                SavedQuestion(
                    id = detail.id,
                    title = detail.title,
                    tags = detail.tags,
                    savedAt = System.currentTimeMillis(),
                )
            val isBookmarked = toggleBookmarkUseCase(savedQuestion)

            sendAction(QuestionDetailAction.BookmarkChanged(isBookmarked))
        }
    }

    /** Submits [choiceId] as the answer to [questionId]. 33IQ itself rejects a second submission. */
    fun onSubmitAnswerClick(
        questionId: Long,
        choiceId: String,
    ) {
        sendAction(QuestionDetailAction.SubmissionStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.submitAnswer(questionId, choiceId)) {
                is Result.Success -> sendAction(QuestionDetailAction.SubmissionFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.SubmissionFailed)
            }
        }
    }

    fun onRevealAnswerClick() {
        sendAction(QuestionDetailAction.AnswerConfirmRequested)
    }

    fun onConfirmRevealAnswer(questionId: Long) {
        sendAction(QuestionDetailAction.AnswerRevealStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.revealAnswer(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.AnswerRevealFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.AnswerFlowFailed)
            }
        }
    }

    /** Fetches 33IQ's own price quote for a hint - it reports the account's actual member-discounted price. */
    fun onRevealHintClick(questionId: Long) {
        sendAction(QuestionDetailAction.HintQuoteStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.quoteHint(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.HintQuoteReady(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed)
            }
        }
    }

    fun onConfirmRevealHint(questionId: Long) {
        sendAction(QuestionDetailAction.HintRevealStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.revealHint(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.HintRevealFinished(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.HintFlowFailed)
            }
        }
    }

    fun onDismissRevealFlow(kind: RevealKind) {
        when (kind) {
            RevealKind.ANSWER -> sendAction(QuestionDetailAction.AnswerFlowDismissed)
            RevealKind.HINT -> sendAction(QuestionDetailAction.HintFlowDismissed)
        }
    }

    fun onPraiseClick(questionId: Long) {
        val currentState = uiStateFlow.value
        if (currentState is QuestionDetailUiState.Content && currentState.isPraising) return

        sendAction(QuestionDetailAction.PraiseStarted)

        viewModelScope.launch {
            when (val result = questionAnswerUseCases.praiseQuestion(questionId)) {
                is Result.Success -> sendAction(QuestionDetailAction.Praised(result.value))
                is Result.Failure -> sendAction(QuestionDetailAction.PraiseFailed)
            }
        }
    }
}
