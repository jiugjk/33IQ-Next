package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository

internal class SubmitAnswerUseCase(
    private val answerRepository: AnswerRepository,
) {
    suspend operator fun invoke(
        questionId: Long,
        context: String,
    ): Result<SubmitAnswerResult> = answerRepository.submitAnswer(questionId, context)
}
