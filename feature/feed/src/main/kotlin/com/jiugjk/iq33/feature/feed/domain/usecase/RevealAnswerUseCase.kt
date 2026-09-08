package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository

internal class RevealAnswerUseCase(
    private val answerRepository: AnswerRepository,
) {
    suspend operator fun invoke(questionId: Long): Result<AnswerReveal> = answerRepository.revealAnswer(questionId)
}
