package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionContentBlock
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import com.jiugjk.iq33.library.network.IqConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

        if (!question.hasQuestionShape()) return null

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

        // #589144 confirms a third interaction type: ischoose=0 still has a server-provided word bank.
        // qc_wronganswer is NOT the word bank; it can omit the correct tile and include different decoys.
        val questionType =
            when {
                question.stringOrNull("ischoose") == "1" -> QuestionType.CHOICE
                question.stringOrNull("is_select_answer") == "1" -> QuestionType.WORD_BANK
                else -> QuestionType.OPEN
            }
        val candidates = question.jsonArrayOrEmpty("select_answer")
        val candidateTexts = candidates.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.contentOrNull }
        val validCandidates =
            candidateTexts
                .takeIf { values ->
                    values.size == candidates.size && values.all { it.isNotBlank() }
                }.orEmpty()

        val bodyHtml = question.stringOrNull("qc_context").orEmpty()
        val parsedBody = parseHtmlContent(bodyHtml, IqConstants.BASE_URL)
        val imageUrls = questionImageUrls(question, parsedBody)

        return QuestionDetail(
            id = id,
            // 33IQ has no real title concept: `qc_title` is an empty string on ordinary questions
            // (confirmed live), and the site's own list pages just print a truncation of the body.
            // So it stays null here rather than being back-filled with a cut-off copy of the body -
            // see QuestionDetail.title.
            title = question.stringOrNull("qc_title")?.takeIf { it.isNotBlank() },
            bodyText = parsedBody.plainText,
            imageUrls = imageUrls,
            bodyBlocks = parsedBody.blocks.ifEmpty { fallbackBlocks(parsedBody.plainText, imageUrls) },
            tags = tags,
            breadcrumb = emptyList(), // No breadcrumb field has been confirmed on the JSON payload.
            author = question.stringOrNull("username"),
            publishedDate = question.stringOrNull("ctime")?.substringBefore(" "),
            upvoteCount = question.intOrZero("praise"),
            isUpvoted = question.stringOrNull("isPraise") == "1",
            commentCount = question.intOrZero("o_commentnum"),
            collectCount = question.intOrZero("o_collectnum"),
            rightRatio = question.stringOrNull("right_ratio")?.toIntOrNull(),
            questionType = questionType,
            choices = choices,
            answerCandidates = if (questionType == QuestionType.WORD_BANK) validCandidates else emptyList(),
            answerLength = if (questionType == QuestionType.WORD_BANK) question.intOrNull("answerStrNum")?.takeIf { it > 0 } else null,
            analysis = null, // 33IQ hides analysis from guests, and no field carrying it has been confirmed.
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
        parsedBody: ParsedHtmlContent,
    ): List<String> {
        if (parsedBody.galleryUrls.isNotEmpty()) return parsedBody.galleryUrls

        return listOfNotNull(question.stringOrNull("pic")?.takeIf { it.isNotBlank() }?.let(::fullSizeImageUrl))
    }

    private fun fallbackBlocks(
        plainText: String,
        imageUrls: List<String>,
    ): List<QuestionContentBlock> {
        val blocks = mutableListOf<QuestionContentBlock>()

        if (plainText.isNotBlank()) {
            blocks += QuestionContentBlock.Text(plainText)
        }
        imageUrls.forEach { url ->
            blocks += QuestionContentBlock.Image(url)
        }

        return blocks
    }

    private fun JsonObject.hasQuestionShape(): Boolean =
        containsKey("qc_context") || containsKey("qc_id") || containsKey("qc_title") || containsKey("pic")

    private fun JsonElement.asFirstObjectOrNull(): JsonObject? = (this as? JsonArray)?.firstOrNull() as? JsonObject

    private companion object {
        val CHOICE_LETTERS = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    }
}
