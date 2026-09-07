package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository

internal class GetQuestionDetailUseCase(
    private val questionRepository: QuestionRepository,
) {
    suspend operator fun invoke(id: Long): Result<QuestionDetail> = questionRepository.getQuestionDetail(id)
}
