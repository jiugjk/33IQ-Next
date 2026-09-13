package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/** Read-only price quote, then explicit payment (if needed), then answer HTML. Never auto-retries payment. */
internal class AnswerRevealRemoteDataSource(
    private val htmlClient: IqHtmlClient,
    private val parsingDispatcher: CoroutineDispatcher,
) {
    suspend fun quote(questionId: Long): AnswerQuote {
        // These are the public web client's no-challenge placeholders, not captured validation tokens.
        // A real captcha/verification response is rejected; this client does not solve or bypass it.
        val fields = CHALLENGE_FIELDS.associateWith { NO_CHALLENGE }
        val json = post(IqConstants.ANSWER_QUOTE_URL, questionId, fields)

        return when (json.stringOrNull(STATUS_FIELD)) {
            // Already entitled: the website fetches straight away, with no second payment.
            STATUS_SHOW -> {
                AnswerQuote(questionId, cost = 0, requiresPayment = false)
            }
            STATUS_SUCCESS -> {
                val cost =
                    json.intOrNull("pay")?.takeIf { it >= 0 }
                        ?: throw failure(IqConstants.ANSWER_QUOTE_URL, json, IqResponseException.Reason.MISSING_FIELD)
                // isLimit here can mean the free allowance is exhausted: the website still offers this paid quote.
                AnswerQuote(questionId, cost, requiresPayment = true)
            }
            else -> {
                throw failure(IqConstants.ANSWER_QUOTE_URL, json)
            }
        }
    }

    suspend fun pay(questionId: Long) {
        val json = post(IqConstants.ANSWER_PAYMENT_URL, questionId)
        if (json.stringOrNull(STATUS_FIELD) != STATUS_SUCCESS) throw failure(IqConstants.ANSWER_PAYMENT_URL, json)
    }

    suspend fun fetch(questionId: Long): AnswerReveal {
        // Matches the web client's per-request nonce shape, without copying a captured nonce/token.
        val random =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(NONCE_SUFFIX_LENGTH)
        val nonce = "35yaobeicaiyuan${System.currentTimeMillis()}hebihuxiangweinan$random"
        val json = post(IqConstants.ANSWER_REVEAL_URL, questionId, mapOf("randstr" to nonce))

        if (json.stringOrNull(STATUS_FIELD) != STATUS_SUCCESS) throw failure(IqConstants.ANSWER_REVEAL_URL, json)

        val answerHtml = json.requiredHtml("answer")
        val explanationHtml = json.requiredHtml("explanation")

        return withContext(parsingDispatcher) {
            val answer = parseHtmlContent(answerHtml, IqConstants.BASE_URL)
            val explanation = parseHtmlContent(explanationHtml, IqConstants.BASE_URL)
            // A paid call that yields no content at all is a failure, not an empty purchase.
            if (answer.blocks.isEmpty() && explanation.blocks.isEmpty()) {
                throw failure(IqConstants.ANSWER_REVEAL_URL, json, IqResponseException.Reason.MISSING_FIELD)
            }
            AnswerReveal(answer.plainText, explanation.plainText, answer.blocks, explanation.blocks)
        }
    }

    /** A non-string (or absent) field is rejected rather than coerced into an empty reveal. */
    private fun JsonObject.requiredHtml(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: throw failure(IqConstants.ANSWER_REVEAL_URL, this, IqResponseException.Reason.MISSING_FIELD)

    private suspend fun post(
        url: String,
        questionId: Long,
        extra: Map<String, String> = emptyMap(),
    ): JsonObject {
        val raw = htmlClient.postWebFormForText(url, mapOf("q_id" to questionId.toString()) + extra)

        return withContext(parsingDispatcher) {
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                ?: throw IqResponseException(url, IqResponseException.Reason.MALFORMED)
        }
    }

    private fun failure(
        url: String,
        json: JsonObject,
        reason: IqResponseException.Reason = IqResponseException.Reason.BUSINESS_ERROR,
    ) = IqResponseException(url, reason, json.stringOrNull(STATUS_FIELD))

    private companion object {
        const val STATUS_FIELD = "status"
        const val STATUS_SUCCESS = "success"
        const val STATUS_SHOW = "show"
        const val NO_CHALLENGE = "default"
        const val NONCE_SUFFIX_LENGTH = 8
        val CHALLENGE_FIELDS = listOf("lot_number", "captcha_output", "pass_token", "gen_time")
    }
}
