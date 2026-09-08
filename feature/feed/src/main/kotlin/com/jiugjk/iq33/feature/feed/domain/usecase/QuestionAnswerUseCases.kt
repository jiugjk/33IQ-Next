package com.jiugjk.iq33.feature.feed.domain.usecase

/** Bundles the answer-submission / paid-reveal use cases to keep QuestionDetailViewModel's constructor short. */
internal data class QuestionAnswerUseCases(
    val submitAnswer: SubmitAnswerUseCase,
    val revealAnswer: RevealAnswerUseCase,
    val quoteHint: QuoteHintUseCase,
    val revealHint: RevealHintUseCase,
    val praiseQuestion: PraiseQuestionUseCase,
)
