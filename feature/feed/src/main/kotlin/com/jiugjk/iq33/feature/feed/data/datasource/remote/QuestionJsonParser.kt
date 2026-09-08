package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import com.jiugjk.iq33.library.network.IqConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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

        val bodyHtml = question.stringOrNull("qc_context").orEmpty()
        val bodyText = htmlToPlainText(bodyHtml)

        return QuestionDetail(
            id = id,
            // 33IQ has no real title concept: `qc_title` is an empty string on ordinary questions
            // (confirmed live), and the site's own list pages just print a truncation of the body.
            // So it stays null here rather than being back-filled with a cut-off copy of the body -
            // see QuestionDetail.title.
            title = question.stringOrNull("qc_title")?.takeIf { it.isNotBlank() },
            bodyText = bodyText,
            imageUrls = questionImageUrls(question, bodyHtml),
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
            // The public page, not the `?p=3` API URL that was fetched: that switch makes the same
            // address serve raw JSON, which is not what a share link should open.
            sourceUrl = IqConstants.questionPageUrl(id),
        )
    }

    /**
     * The question's images, at their original resolution.
     *
     * The body's own `<img>` tags come first (they are the ones placed in the text, in the order the
     * author placed them). `pic` - the payload's separate full-size image field - is only used when
     * the body embeds none at all: for questions that have both it is the same upload, and once both
     * are normalised by [fullSizeImageUrl] the duplicate would be identical anyway.
     */
    private fun questionImageUrls(
        question: JsonObject,
        bodyHtml: String,
    ): List<String> {
        val bodyImages = htmlImageUrls(bodyHtml, IqConstants.BASE_URL)

        if (bodyImages.isNotEmpty()) return bodyImages

        return listOfNotNull(question.stringOrNull("pic")?.takeIf { it.isNotBlank() }?.let(::fullSizeImageUrl))
    }

    private fun JsonElement.asFirstObjectOrNull(): JsonObject? = (this as? JsonArray)?.firstOrNull() as? JsonObject

    private companion object {
        val CHOICE_LETTERS = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    }
}
