package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

/**
 * Everything the detail screen can ask its view model to do.
 *
 * The content composable takes this one callback instead of a handful of same-shaped lambdas, so a
 * wiring mistake becomes a compile error rather than a silent mix-up, and the composable stays
 * previewable without a view model.
 */
internal sealed interface QuestionDetailEvent {
    data class ChoiceSelected(
        val choiceId: String,
    ) : QuestionDetailEvent

    data class DraftAnswerChanged(
        val text: String,
    ) : QuestionDetailEvent

    data class AnswerSubmitted(
        val answer: String,
    ) : QuestionDetailEvent

    data object BookmarkToggled : QuestionDetailEvent

    data object HintQuoteRequested : QuestionDetailEvent

    data object HintRevealConfirmed : QuestionDetailEvent

    /** Closes the hint's price-confirmation dialog without buying it. */
    data object HintFlowDismissed : QuestionDetailEvent

    data object PraiseClicked : QuestionDetailEvent

    /** Retries a question whose initial load failed. */
    data object RetryRequested : QuestionDetailEvent
}
