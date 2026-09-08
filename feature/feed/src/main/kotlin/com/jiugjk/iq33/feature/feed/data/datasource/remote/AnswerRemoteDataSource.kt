package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Wires 33IQ's real answer-submission / paid-reveal endpoints - confirmed from a HAR capture of a
 * real logged-in Android app session actually submitting answers, buying hints and viewing answers.
 * See [IqConstants] for the endpoint URLs and what's confirmed about each.
 *
 * Every endpoint validates the fields it needs before building a result: these endpoints report
 * business failures as an HTTP 200 JSON body, so an unvalidated response would silently become an
 * empty answer, a free-looking price quote or an upvote count of 0. See [IqResponseException].
 */
internal class AnswerRemoteDataSource(
    private val htmlClient: IqHtmlClient,
    private val parsingDispatcher: CoroutineDispatcher,
) {
    suspend fun submitAnswer(
        questionId: Long,
        answer: String,
    ): SubmitAnswerResult {
        val json =
            postForJson(
                SUBMIT_ANSWER,
                IqConstants.SUBMIT_ANSWER_URL,
                mapOf(
                    "type" to "comment",
                    "sina_post" to "0",
                    "qq_post" to "0",
                    // 33IQ's own form field name for the submitted answer body.
                    "context" to answer,
                    "id" to questionId.toString(),
                    "isanswer" to "1",
                    "action" to "comment",
                ),
            )

        return when (json.stringOrNull(STATUS_FIELD)) {
            // 学识 numbers are reported as nullable: a missing field means "not reported", which is
            // not the same as a score change of 0.
            "success" -> SubmitAnswerResult.Correct(scoreDelta = json.intOrNull("score"), myScore = json.intOrNull("myScore"))
            "wrong" -> SubmitAnswerResult.Wrong(scoreDelta = json.intOrNull("score"), myScore = json.intOrNull("myScore"))
            "repeat" -> SubmitAnswerResult.AlreadyAnswered
            else ->
                if (json.stringOrNull("isLimit") == "1") {
                    SubmitAnswerResult.LimitReached
                } else {
                    throw json.failure(SUBMIT_ANSWER, IqResponseException.Reason.MISSING_FIELD)
                }
        }
    }

    /**
     * Reveals the real answer and explanation, spending 学识. Calls `showanswertrue`,
     * `payforshowanswer` and `showanswernew`, **in that exact order** - the real app's own order.
     * An earlier version of this client called `payforshowanswer` first (to show a cost-confirmation
     * dialog before spending anything), which broke this flow at runtime ("网络异常" on a real user's
     * account) - so the exact server-side dependency between these three calls isn't understood well
     * enough to reorder them again; this client would rather match the real sequence than guess.
     *
     * Each step is checked before the next one runs, so a business failure cannot be carried forward
     * into a "revealed" state with no answer in it. From step two onwards a failure is flagged as
     * [IqResponseException.afterSideEffect]: the first call already reached the server, and which of
     * the three actually spends 学识 is not confirmed.
     */
    suspend fun revealAnswer(questionId: Long): AnswerReveal {
        val params = questionIdParams(questionId)

        val answerJson = postForJson(SHOW_ANSWER_TRUE, IqConstants.SHOW_ANSWER_TRUE_URL, params).requireSuccess(SHOW_ANSWER_TRUE)
        val answer = answerJson.requireString("answer", SHOW_ANSWER_TRUE)

        val payJson =
            postForJson(PAY_FOR_SHOW_ANSWER, IqConstants.PAY_FOR_SHOW_ANSWER_URL, params)
                .requireSuccess(PAY_FOR_SHOW_ANSWER, afterSideEffect = true)
        val alreadyPaid = payJson.stringOrNull("isPaid") == "1"
        // An unparseable price is reported as unknown rather than quietly shown to the user as 0.
        val cost = if (alreadyPaid) 0 else payJson.intOrNull("pay")

        postForJson(SHOW_ANSWER_NEW, IqConstants.SHOW_ANSWER_NEW_URL, params)
            .requireSuccess(SHOW_ANSWER_NEW, afterSideEffect = true)

        return AnswerReveal(
            answer = answer,
            // `explanation` is rich-text HTML (raw <p>/<br>/&nbsp; and the like), same as a question's
            // own qc_context body - it must be converted to plain text rather than rendered as-is.
            explanation = withContext(parsingDispatcher) { answerJson.stringOrNull("explanation")?.let(::htmlToPlainText).orEmpty() },
            cost = cost,
            alreadyPaid = alreadyPaid,
        )
    }

    /** Price quote for a hint, with 33IQ's own per-membership-tier pricing - call before [revealHint]. */
    suspend fun quoteHint(questionId: Long): HintQuote {
        val json =
            postForJson(SHOW_TIPS_BUY, IqConstants.SHOW_TIPS_BUY_URL, questionIdParams(questionId))
                .requireSuccess(SHOW_TIPS_BUY)

        val payType = json.stringOrNull("paytype")
        val normal = json.intOrNull("answerpay")
        val member = json.intOrNull("memberpay")
        val lifeMember = json.intOrNull("lifeMemberpay")

        // The cost the user is actually asked to confirm has to be a real number: refusing here is
        // what stops the confirm dialog from offering to spend an unknown amount of 学识.
        val effective =
            when (payType) {
                "memberpay" -> member
                "lifeMemberpay" -> lifeMember
                else -> normal
            } ?: throw json.failure(SHOW_TIPS_BUY, IqResponseException.Reason.MISSING_FIELD)

        return HintQuote(normalCost = normal, memberCost = member, lifeMemberCost = lifeMember, effectiveCost = effective)
    }

    suspend fun revealHint(questionId: Long): HintReveal {
        val json =
            postForJson(SHOW_TIPS, IqConstants.SHOW_TIPS_URL, questionIdParams(questionId))
                .requireSuccess(SHOW_TIPS)

        return HintReveal(tips = json.requireString("tips", SHOW_TIPS))
    }

    /** Praises ("点赞") a question, returning the new upvote count. */
    suspend fun praiseQuestion(questionId: Long): Int {
        val json =
            postForJson(
                PRAISE,
                IqConstants.PRAISE_URL,
                mapOf("q_id" to questionId.toString(), "type" to "question"),
            ).requireSuccess(PRAISE)

        // Confirmed shape: {"status":"success","num":"<new upvote count>"}. Without a parseable
        // count there is no new total to show, and reporting 0 would look like every like was lost.
        return json.intOrNull("num") ?: throw json.failure(PRAISE, IqResponseException.Reason.MISSING_FIELD)
    }

    private fun questionIdParams(questionId: Long) = mapOf("q_id" to questionId.toString())

    /** Every one of this class's endpoints requires [IqConstants.ACTION_QUERY_SUFFIX] - see its doc. */
    private suspend fun postForJson(
        endpoint: String,
        url: String,
        params: Map<String, String>,
    ): JsonObject {
        val rawJson = htmlClient.postFormForText(url + IqConstants.ACTION_QUERY_SUFFIX, params)

        // JSON parsing is CPU work on a possibly large body - it must not land on the caller's
        // (main) thread just because the HTTP call already returned.
        return withContext(parsingDispatcher) {
            runCatching { Json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject
                ?: throw IqResponseException(endpoint, IqResponseException.Reason.MALFORMED)
        }
    }

    private fun JsonObject.requireSuccess(
        endpoint: String,
        afterSideEffect: Boolean = false,
    ): JsonObject {
        val status = stringOrNull(STATUS_FIELD)

        if (status != null && status.lowercase() in ERROR_STATUSES) {
            throw failure(endpoint, IqResponseException.Reason.BUSINESS_ERROR, afterSideEffect)
        }

        return this
    }

    private fun JsonObject.requireString(
        key: String,
        endpoint: String,
    ): String =
        stringOrNull(key)?.takeIf { it.isNotBlank() }
            ?: throw failure(endpoint, IqResponseException.Reason.MISSING_FIELD, afterSideEffect = endpoint != SHOW_ANSWER_TRUE)

    private fun JsonObject.failure(
        endpoint: String,
        reason: IqResponseException.Reason,
        afterSideEffect: Boolean = false,
    ) = IqResponseException(endpoint, reason, stringOrNull(STATUS_FIELD), afterSideEffect)

    private companion object {
        const val STATUS_FIELD = "status"
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1", "nologin", "guest")

        const val SUBMIT_ANSWER = "commentdeal"
        const val SHOW_ANSWER_TRUE = "showanswertrue"
        const val PAY_FOR_SHOW_ANSWER = "payforshowanswer"
        const val SHOW_ANSWER_NEW = "showanswernew"
        const val SHOW_TIPS = "showtips"
        const val SHOW_TIPS_BUY = "showtipsbuy"
        const val PRAISE = "praise"
    }
}
