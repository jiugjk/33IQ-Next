package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wires 33IQ's real answer-submission / paid-hint endpoints - confirmed from a HAR capture of a real
 * logged-in Android app session actually submitting answers and buying hints.
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
            "seeanswer" -> SubmitAnswerResult.AnswerAlreadyViewed
            else ->
                if (json.stringOrNull("isLimit") == "1") {
                    SubmitAnswerResult.LimitReached
                } else {
                    throw json.failure(SUBMIT_ANSWER, IqResponseException.Reason.MISSING_FIELD)
                }
        }
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
        val json = postPaidHint(questionId).requireSuccess(SHOW_TIPS)

        // showtips is the call that spends the 学识, so a reply without hint text may still have
        // charged the account - the caller must not present that as "nothing happened".
        return HintReveal(tips = json.requireString("tips", SHOW_TIPS, afterSideEffect = true))
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

    /**
     * [IqConstants.SHOW_TIPS_URL] is the call that spends 学识. Any failure after the request is
     * attempted is treated as an unknown paid outcome so the UI cannot present it as a safe retry.
     */
    private suspend fun postPaidHint(questionId: Long): JsonObject {
        val result =
            runCatching {
                postForJson(SHOW_TIPS, IqConstants.SHOW_TIPS_URL, questionIdParams(questionId))
            }
        val error = result.exceptionOrNull() ?: return result.getOrThrow()

        if (error is CancellationException) throw error

        throw when (error) {
            is IqResponseException ->
                IqResponseException(error.endpoint, error.reason, error.status, afterSideEffect = true, cause = error)
            is IOException ->
                IqResponseException(SHOW_TIPS, IqResponseException.Reason.MALFORMED, afterSideEffect = true, cause = error)
            else ->
                IqResponseException(SHOW_TIPS, IqResponseException.Reason.MALFORMED, afterSideEffect = true, cause = error)
        }
    }

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

    /**
     * Rejects a reply whose `status` is one this endpoint cannot make progress from.
     *
     * Every remaining endpoint is a single call whose reply is either the thing that was asked for
     * or a failure, so they all share the one [ERROR_STATUSES] set. The narrower per-call override
     * this used to take existed only for the multi-step answer-reveal flow, which is gone.
     */
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
        afterSideEffect: Boolean = false,
    ): String =
        stringOrNull(key)?.takeIf(String::isNotBlank)
            ?: throw failure(endpoint, IqResponseException.Reason.MISSING_FIELD, afterSideEffect)

    private fun JsonObject.failure(
        endpoint: String,
        reason: IqResponseException.Reason,
        afterSideEffect: Boolean = false,
    ) = IqResponseException(endpoint, reason, stringOrNull(STATUS_FIELD), afterSideEffect)

    private companion object {
        const val STATUS_FIELD = "status"

        /**
         * For single-call endpoints, whose reply is either the thing that was asked for or a
         * failure. Deliberately broad: there is no third outcome to leave room for.
         */
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1", "nologin", "guest")

        const val SUBMIT_ANSWER = "commentdeal"
        const val SHOW_TIPS = "showtips"
        const val SHOW_TIPS_BUY = "showtipsbuy"
        const val PRAISE = "praise"
    }
}
