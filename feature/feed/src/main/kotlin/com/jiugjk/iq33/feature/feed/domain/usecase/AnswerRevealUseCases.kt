package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRevealRepository

internal class QuoteAnswerUseCase(
    private val answerRevealRepository: AnswerRevealRepository,
) {
    suspend operator fun invoke(questionId: Long) = answerRevealRepository.quote(questionId)
}

internal class RevealAnswerUseCase(
    private val answerRevealRepository: AnswerRevealRepository,
) {
    suspend operator fun invoke(quote: AnswerQuote) = answerRevealRepository.reveal(quote)
}

internal class RecoverAnswerUseCase(
    private val answerRevealRepository: AnswerRevealRepository,
) {
    suspend operator fun invoke(questionId: Long) = answerRevealRepository.recover(questionId)
}

internal data class AnswerRevealUseCases(
    val quote: QuoteAnswerUseCase,
    val reveal: RevealAnswerUseCase,
    val recover: RecoverAnswerUseCase,
)
