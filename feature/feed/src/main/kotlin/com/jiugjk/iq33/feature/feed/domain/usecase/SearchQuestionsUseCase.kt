package com.jiugjk.iq33.feature.feed.domain.usecase

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository

internal class SearchQuestionsUseCase(
    private val questionRepository: QuestionRepository,
) {
    suspend operator fun invoke(
        keyword: String,
        page: Int,
    ): Result<List<QuestionSummary>> = questionRepository.searchQuestions(keyword, page)
}
