package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository

internal class QuoteAnswerUseCase(
    private val answerRepository: AnswerRepository,
) {
    suspend operator fun invoke(questionId: Long): Result<AnswerQuote> = answerRepository.quoteAnswer(questionId)
}
