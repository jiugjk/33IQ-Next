package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

internal interface QuestionRepository {
    suspend fun getQuestionList(
        category: Category,
        page: Int,
    ): Result<List<QuestionSummary>>

    suspend fun searchQuestions(
        keyword: String,
        page: Int,
    ): Result<List<QuestionSummary>>

    suspend fun getQuestionDetail(id: Long): Result<QuestionDetail>
}
