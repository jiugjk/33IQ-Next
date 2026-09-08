package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

internal interface AnswerRepository {
    /** Submits [answer] - the chosen option's id for a choice question - as this account's answer to [questionId]. */
    suspend fun submitAnswer(
        questionId: Long,
        answer: String,
    ): Result<SubmitAnswerResult>

    suspend fun revealAnswer(questionId: Long): Result<AnswerReveal>

    suspend fun quoteHint(questionId: Long): Result<HintQuote>

    suspend fun revealHint(questionId: Long): Result<HintReveal>

    /** Praises ("点赞") a question, returning the new upvote count. */
    suspend fun praiseQuestion(questionId: Long): Result<Int>
}
