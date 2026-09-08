package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
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

    /** Price quote for revealing the real answer - call before [revealAnswer] to show a confirmation. */
    suspend fun quoteAnswer(questionId: Long): AnswerQuote {
        val json = postForJson(IqConstants.PAY_FOR_SHOW_ANSWER_URL, questionIdParams(questionId))

        return AnswerQuote(
            cost = json.intOrZero("pay"),
            alreadyPaid = json.stringOrNull("isPaid") == "1",
        )
    }

    /**
     * Reveals the real answer and explanation. Mirrors the real app's call sequence - it also calls
     * `showanswernew` after fetching the content, since that call's role (if any) beyond `showanswertrue`
     * in actually spending 学识 isn't confirmed and this client would rather match the real sequence
     * than risk skipping a step the server depends on.
     */
    suspend fun revealAnswer(questionId: Long): AnswerReveal {
        val params = questionIdParams(questionId)
        val answerJson = postForJson(IqConstants.SHOW_ANSWER_TRUE_URL, params)
        postForJson(IqConstants.SHOW_ANSWER_NEW_URL, params)

        return AnswerReveal(
            answer = answerJson.stringOrNull("answer").orEmpty(),
            // `explanation` is rich-text HTML (raw <p>/<br>/&nbsp; and the like), same as a question's
            // own qc_context body - it must be converted to plain text rather than rendered as-is.
            explanation = answerJson.stringOrNull("explanation")?.let(::htmlToPlainText).orEmpty(),
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

    private fun questionIdParams(questionId: Long) = mapOf("q_id" to questionId.toString())

    private suspend fun postForJson(
        url: String,
        params: Map<String, String>,
    ): JsonObject {
        val rawJson = htmlClient.postFormForText(url, params)

        return Json.parseToJsonElement(rawJson) as? JsonObject
            ?: throw IOException("Unexpected non-object JSON response from $url")
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intOrZero(key: String): Int = stringOrNull(key)?.toIntOrNull() ?: 0
}
