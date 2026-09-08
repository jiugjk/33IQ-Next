package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

internal interface AnswerRepository {
    suspend fun submitAnswer(
        questionId: Long,
        context: String,
    ): Result<SubmitAnswerResult>

    suspend fun quoteAnswer(questionId: Long): Result<AnswerQuote>

    suspend fun revealAnswer(questionId: Long): Result<AnswerReveal>

    suspend fun quoteHint(questionId: Long): Result<HintQuote>

    suspend fun revealHint(questionId: Long): Result<HintReveal>
}
