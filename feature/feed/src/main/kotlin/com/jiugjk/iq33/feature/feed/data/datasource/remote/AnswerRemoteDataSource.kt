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
     * **Which of the three actually returns the answer is not confirmed**, and assuming it was the
     * first one is what used to make this flow fail for a logged-in user whose hints worked fine:
     * `showanswertrue` reads as an eligibility check ("may this answer be shown?"), so a reply of
     * `{"status":"0"}` - not yet unlocked, the normal state before paying - was rejected as a
     * business error, and even a passing reply was rejected for not carrying an `answer` field that
     * only a later step may ever have had. The content is therefore taken from whichever step
     * supplies it, latest first, since `showanswernew` is the call that *shows* the answer; only a
     * flow where no step at all supplied one is a failure. Statuses are still checked, but against
     * the values that really mean refusal (not signed in, server-side error) rather than against
     * every falsy-looking string.
     *
     * Each step is still checked before the next one runs, so a refusal cannot be carried forward
     * into a "revealed" state with no answer in it. From step two onwards a failure is flagged as
     * [IqResponseException.afterSideEffect]: the first call already reached the server, and which of
     * the three spends the 学识 is not confirmed either.
     */
    suspend fun revealAnswer(questionId: Long): AnswerReveal {
        val params = questionIdParams(questionId)

        val checkJson =
            postForJson(SHOW_ANSWER_TRUE, IqConstants.SHOW_ANSWER_TRUE_URL, params)
                .requireSuccess(SHOW_ANSWER_TRUE, fatalStatuses = REFUSAL_STATUSES)

        val payJson =
            postForJson(PAY_FOR_SHOW_ANSWER, IqConstants.PAY_FOR_SHOW_ANSWER_URL, params)
                .requireSuccess(PAY_FOR_SHOW_ANSWER, afterSideEffect = true, fatalStatuses = REFUSAL_STATUSES)

        val revealJson =
            postForJson(SHOW_ANSWER_NEW, IqConstants.SHOW_ANSWER_NEW_URL, params)
                .requireSuccess(SHOW_ANSWER_NEW, afterSideEffect = true, fatalStatuses = REFUSAL_STATUSES)

        // Latest step first: showanswernew is the one that actually shows the answer, so when more
        // than one step carries the field its copy is the authoritative one.
        val steps = listOf(revealJson, payJson, checkJson)

        val answer =
            steps.firstNotNullOfOrNull { step -> step.stringOrNull(ANSWER_FIELD)?.takeIf(String::isNotBlank) }
                ?: throw revealJson.failure(SHOW_ANSWER_NEW, IqResponseException.Reason.MISSING_FIELD, afterSideEffect = true)

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
        const val SHOW_ANSWER_NEW = "showanswernew"
        const val SHOW_TIPS = "showtips"
        const val SHOW_TIPS_BUY = "showtipsbuy"
        const val PRAISE = "praise"
    }
}
