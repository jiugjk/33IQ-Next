package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

/**
 * Everything the detail screen can ask its view model to do.
 *
 * The content composable takes this one callback instead of nine same-shaped lambdas, so a wiring
 * mistake (confirm-answer hooked to confirm-hint, say) becomes a compile error rather than a silent
 * mix-up, and the composable stays previewable without a view model.
 */
internal sealed interface QuestionDetailEvent {
    data class ChoiceSelected(
        val choiceId: String,
    ) : QuestionDetailEvent

    data class AnswerSubmitted(
        val choiceId: String,
    ) : QuestionDetailEvent

    data object BookmarkToggled : QuestionDetailEvent

    data object AnswerRevealRequested : QuestionDetailEvent

    data object AnswerRevealConfirmed : QuestionDetailEvent

    data object HintQuoteRequested : QuestionDetailEvent

    data object HintRevealConfirmed : QuestionDetailEvent

    data class RevealFlowDismissed(
        val kind: RevealKind,
    ) : QuestionDetailEvent

    data object PraiseClicked : QuestionDetailEvent

    /** Retries a question whose initial load failed. */
    data object RetryRequested : QuestionDetailEvent
}
