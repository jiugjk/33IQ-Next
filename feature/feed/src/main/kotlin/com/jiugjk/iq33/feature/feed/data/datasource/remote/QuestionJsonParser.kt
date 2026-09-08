package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses 33IQ's app-facing question detail JSON - `GET /question/<id>.html?p=3` - discovered and
 * verified from a real captured Android app session (the same URL guests/browsers get as HTML).
 *
 * The payload is a loosely-typed legacy PHP API: a one-element JSON array of an object with dozens
 * of string-typed fields (including booleans as `"0"`/`"1"`). Fields not confirmed here (e.g. the
 * real answer/analysis text) are left absent rather than guessed.
 */
internal class QuestionJsonParser {
    fun parseQuestionDetail(
        rawJson: String,
        id: Long,
        sourceUrl: String,
    ): QuestionDetail? {
        val question = Json.parseToJsonElement(rawJson).asFirstObjectOrNull() ?: return null

        val tags =
            (
                question.stringList("tag_micro") +
                    question.jsonArrayOrEmpty("topicTag").mapNotNull { entry ->
                        (entry as? JsonObject)?.stringOrNull("gtt_name")
                    }
            ).distinct()

        val choices =
            CHOICE_LETTERS.mapNotNull { letter ->
                question
                    .stringOrNull("qc_choose_$letter")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { text -> Choice(id = letter.uppercase(), text = text) }
            }

        val bodyText = htmlToText(question, "qc_context")
        val title = question.stringOrNull("qc_title")?.takeIf { it.isNotBlank() } ?: bodyText.take(TITLE_MAX_LENGTH)

        return QuestionDetail(
            id = id,
            title = title,
            tags = tags,
            breadcrumb = emptyList(),
            author = question.stringOrNull("username"),
            publishedDate = question.stringOrNull("ctime")?.substringBefore(" "),
            upvoteCount = question.intOrZero("praise"),
            isUpvoted = question.stringOrNull("isPraise") == "1",
            commentCount = question.intOrZero("o_commentnum"),
            collectCount = question.intOrZero("o_collectnum"),
            rightRatio = question.stringOrNull("right_ratio")?.toIntOrNull(),
            questionType = if (question.stringOrNull("ischoose") == "1") QuestionType.CHOICE else QuestionType.OPEN,
            choices = choices,
            analysis = null,
            comments = emptyList(),
            sourceUrl = sourceUrl,
        )
    }

    private fun htmlToText(
        question: JsonObject,
        field: String,
    ): String = question.stringOrNull(field)?.let(::htmlToPlainText) ?: ""

    private fun JsonElement.asFirstObjectOrNull(): JsonObject? = (this as? JsonArray)?.firstOrNull() as? JsonObject

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intOrZero(key: String): Int = stringOrNull(key)?.toIntOrNull() ?: 0

    private fun JsonObject.jsonArrayOrEmpty(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.stringList(key: String): List<String> = jsonArrayOrEmpty(key).mapNotNull { it.jsonPrimitive.contentOrNull }

    private companion object {
        val CHOICE_LETTERS = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        const val TITLE_MAX_LENGTH = 60
    }
}
