package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Wires 33IQ's real answer-submission / paid-reveal endpoints - confirmed from a HAR capture of a
 * real logged-in Android app session actually submitting answers, buying hints and viewing answers.
 * See [IqConstants] for the endpoint URLs and what's confirmed about each.
 */
internal class AnswerRemoteDataSource(
    private val htmlClient: IqHtmlClient,
) {
    suspend fun submitAnswer(
        questionId: Long,
        context: String,
    ): SubmitAnswerResult {
        val json =
            postForJson(
                IqConstants.SUBMIT_ANSWER_URL,
                mapOf(
                    "type" to "comment",
                    "sina_post" to "0",
                    "qq_post" to "0",
                    "context" to context,
                    "id" to questionId.toString(),
                    "isanswer" to "1",
                    "action" to "comment",
                ),
            )

        return when (json.stringOrNull("status")) {
            "success" ->
                SubmitAnswerResult.Correct(
                    scoreDelta = json.intOrZero("score"),
                    myScore = json.intOrZero("myScore"),
                )
            "wrong" ->
                SubmitAnswerResult.Wrong(
                    scoreDelta = json.intOrZero("score"),
                    myScore = json.intOrZero("myScore"),
                )
            "repeat" -> SubmitAnswerResult.AlreadyAnswered
            else ->
                if (json.stringOrNull("isLimit") == "1") {
                    SubmitAnswerResult.LimitReached
                } else {
                    throw IOException("Unexpected submit-answer response shape for id=$questionId")
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
     */
    suspend fun revealAnswer(questionId: Long): AnswerReveal {
        val params = questionIdParams(questionId)
        val answerJson = postForJson(IqConstants.SHOW_ANSWER_TRUE_URL, params)
        val payJson = postForJson(IqConstants.PAY_FOR_SHOW_ANSWER_URL, params)
        postForJson(IqConstants.SHOW_ANSWER_NEW_URL, params)

        return AnswerReveal(
            answer = answerJson.stringOrNull("answer").orEmpty(),
            // `explanation` is rich-text HTML (raw <p>/<br>/&nbsp; and the like), same as a question's
            // own qc_context body - it must be converted to plain text rather than rendered as-is.
            explanation = answerJson.stringOrNull("explanation")?.let(::htmlToPlainText).orEmpty(),
            cost = payJson.intOrZero("pay"),
            alreadyPaid = payJson.stringOrNull("isPaid") == "1",
        )
    }

    /** Price quote for a hint, with 33IQ's own per-membership-tier pricing - call before [revealHint]. */
    suspend fun quoteHint(questionId: Long): HintQuote {
        val json = postForJson(IqConstants.SHOW_TIPS_BUY_URL, questionIdParams(questionId))

        val normal = json.intOrZero("answerpay")
        val member = json.intOrZero("memberpay")
        val lifeMember = json.intOrZero("lifeMemberpay")
        val effective =
            when (json.stringOrNull("paytype")) {
                "memberpay" -> member
                "lifeMemberpay" -> lifeMember
                else -> normal
            }

        return HintQuote(normalCost = normal, memberCost = member, lifeMemberCost = lifeMember, effectiveCost = effective)
    }

    suspend fun revealHint(questionId: Long): HintReveal {
        val json = postForJson(IqConstants.SHOW_TIPS_URL, questionIdParams(questionId))

        return HintReveal(tips = json.stringOrNull("tips").orEmpty())
    }

    /** Praises ("点赞") a question, returning the new upvote count. */
    suspend fun praiseQuestion(questionId: Long): Int {
        val json =
            postForJson(
                IqConstants.PRAISE_URL,
                mapOf("q_id" to questionId.toString(), "type" to "question"),
            )

        return json.intOrZero("num")
    }

    private fun questionIdParams(questionId: Long) = mapOf("q_id" to questionId.toString())

    /** Every one of this class's endpoints requires [IqConstants.ACTION_QUERY_SUFFIX] - see its doc. */
    private suspend fun postForJson(
        url: String,
        params: Map<String, String>,
    ): JsonObject {
        val rawJson = htmlClient.postFormForText(url + IqConstants.ACTION_QUERY_SUFFIX, params)

        return Json.parseToJsonElement(rawJson) as? JsonObject
            ?: throw IOException("Unexpected non-object JSON response from $url")
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intOrZero(key: String): Int = stringOrNull(key)?.toIntOrNull() ?: 0
}
