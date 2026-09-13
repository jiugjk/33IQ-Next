package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

internal interface AnswerRepository {
    /**
     * Submits [answer] - the chosen option's id for a choice question - as this account's answer to
     * [questionId].
     *
     * [title] is the question's one-line label, carried through because the history row and the
     * 学识 change log are both written from here, before any screen gets a chance to fill it in.
     * Blank when the caller has no detail loaded; a blank never overwrites a title already stored.
     */
    suspend fun submitAnswer(
        questionId: Long,
        answer: String,
        title: String = "",
    ): Result<SubmitAnswerResult>

    suspend fun quoteHint(questionId: Long): Result<HintQuote>

    suspend fun revealHint(questionId: Long): Result<HintReveal>

    /** Praises ("点赞") a question, returning the new upvote count. */
    suspend fun praiseQuestion(questionId: Long): Result<Int>
}
