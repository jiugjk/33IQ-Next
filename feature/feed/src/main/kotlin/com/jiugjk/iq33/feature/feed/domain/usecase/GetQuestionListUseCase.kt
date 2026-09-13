package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository

internal class GetQuestionListUseCase(
    private val questionRepository: QuestionRepository,
) {
    suspend operator fun invoke(
        category: Category,
        nextPageUrl: String?,
    ): Result<QuestionPage> = questionRepository.getQuestionList(category, nextPageUrl)
}
