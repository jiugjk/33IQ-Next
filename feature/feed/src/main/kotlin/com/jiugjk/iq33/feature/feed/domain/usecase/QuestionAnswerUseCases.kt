package com.jiugjk.iq33.feature.feed.domain.usecase

/** Bundles the answer-submission / paid-hint use cases to keep QuestionDetailViewModel's constructor short. */
internal data class QuestionAnswerUseCases(
    val submitAnswer: SubmitAnswerUseCase,
    val quoteHint: QuoteHintUseCase,
    val revealHint: RevealHintUseCase,
    val praiseQuestion: PraiseQuestionUseCase,
)
