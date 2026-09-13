package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal

internal interface AnswerRevealRepository {
    suspend fun quote(questionId: Long): Result<AnswerQuote>

    suspend fun reveal(quote: AnswerQuote): Result<AnswerReveal>

    /** Fetch only; must never call the payment endpoint, including after app restart. */
    suspend fun recover(questionId: Long): Result<AnswerReveal>
}
