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
     * Reveals the real answer and explanation, spending 学识.
     *
     * The order here is **pay first, then show**, and it is confirmed rather than inferred: a HAR
     * capture of the official Android app (3.6.3) revealing question 108950 shows exactly two calls,
     * 534ms apart on one connection, and no others:
     *
     * 1. `payforshowanswer` -> `{"status":"success","isPaid":"0","pay":"60","shownum":"4009","todySeeNum":"0"}`
     * 2. `showanswertrue`   -> `{"answer":"A","explanation":"<p>...</p>","isChangeWrongData":"0","status":"success"}`
     *
     * So `payforshowanswer` is the call that buys the reveal and reports what it cost, and
     * `showanswertrue` ("show answer, truly") is the one that hands the answer over afterwards.
     * Asking `showanswertrue` for the answer *before* paying - which is what this client used to do -
     * is why revealing failed for a signed-in account whose hints worked fine: the answer is simply
     * not something the server will hand out yet at that point.
     *
     * `showanswernew` is not called at all: it appears nowhere in the capture, and there is no answer
     * left for it to fetch once step 2 has returned one.
     *
     * The step-two failure is flagged as [IqResponseException.afterSideEffect] because by then the
     * purchase has already gone through - a reveal that dies there may well have cost 学识 anyway,
     * and the caller must not present it as "nothing happened".
     */
    suspend fun revealAnswer(questionId: Long): AnswerReveal {
        val params = questionIdParams(questionId)

        val payJson =
            postForJson(PAY_FOR_SHOW_ANSWER, IqConstants.PAY_FOR_SHOW_ANSWER_URL, params)
                .requireSuccess(PAY_FOR_SHOW_ANSWER, fatalStatuses = REFUSAL_STATUSES)

        val revealJson =
            postForJson(SHOW_ANSWER_TRUE, IqConstants.SHOW_ANSWER_TRUE_URL, params)
                .requireSuccess(SHOW_ANSWER_TRUE, afterSideEffect = true, fatalStatuses = REFUSAL_STATUSES)

        // The reveal step is where the answer comes from; the purchase step is only checked as a
        // fallback, so a server that ever answers earlier than expected still works.
        val steps = listOf(revealJson, payJson)

        val answer =
            steps.firstNotNullOfOrNull { step -> step.stringOrNull(ANSWER_FIELD)?.takeIf(String::isNotBlank) }
                ?: throw revealJson.failure(SHOW_ANSWER_TRUE, IqResponseException.Reason.MISSING_FIELD, afterSideEffect = true)

        val alreadyPaid = payJson.stringOrNull("isPaid") == "1"
        // An unparseable price is reported as unknown rather than quietly shown to the user as 0.
        val cost = if (alreadyPaid) 0 else payJson.intOrNull("pay")
        val explanationHtml = steps.firstNotNullOfOrNull { step -> step.stringOrNull(EXPLANATION_FIELD)?.takeIf(String::isNotBlank) }

        return AnswerReveal(
            answer = answer,
            // `explanation` is rich-text HTML (raw <p>/<br>/&nbsp; and the like), same as a question's
            // own qc_context body - it must be converted to plain text rather than rendered as-is.
            explanation = withContext(parsingDispatcher) { explanationHtml?.let(::htmlToPlainText).orEmpty() },
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
     * [fatalStatuses] is narrowed to [REFUSAL_STATUSES] by the multi-step answer-reveal flow, whose
     * calls report *progress* through `status` rather than only success or failure - see
     * [revealAnswer]. Single-call endpoints keep the broad [ERROR_STATUSES] set: they have no later
     * step that could still turn a falsy status into a result.
     */
    private fun JsonObject.requireSuccess(
        endpoint: String,
        afterSideEffect: Boolean = false,
        fatalStatuses: Set<String> = ERROR_STATUSES,
    ): JsonObject {
        val status = stringOrNull(STATUS_FIELD)

        if (status != null && status.lowercase() in fatalStatuses) {
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
        const val ANSWER_FIELD = "answer"
        const val EXPLANATION_FIELD = "explanation"

        /**
         * For single-call endpoints, whose reply is either the thing that was asked for or a
         * failure. Deliberately broad: there is no third outcome to leave room for.
         */
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1", "nologin", "guest")

        /** Statuses that mean an outright refusal, whatever step of a multi-call flow reports one. */
        val REFUSAL_STATUSES = setOf("error", "fail", "failed", "nologin", "guest")

        const val SUBMIT_ANSWER = "commentdeal"
        const val SHOW_ANSWER_TRUE = "showanswertrue"
        const val PAY_FOR_SHOW_ANSWER = "payforshowanswer"
        const val SHOW_TIPS = "showtips"
        const val SHOW_TIPS_BUY = "showtipsbuy"
        const val PRAISE = "praise"
    }
}
