package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test

class CopyQuestionTest {
    @Test
    fun `the copied text carries the whole body and every choice`() {
        val text = questionAsPlainText(detail())

        text shouldBeEqualTo
            listOf(
                "男子被发现死在自家浴室。",
                "",
                "第二段正文。",
                "",
                "A. 冰块",
                "B. 毛巾冰棒",
            ).joinToString("\n")
    }

    @Test
    fun `the copied text does not append the question's link`() {
        val text = questionAsPlainText(detail())

        // Copying is for pasting the question itself; the link is what sharing hands out.
        text shouldNotContain "33iq.com"
        text.last().toString() shouldBeEqualTo "棒"
    }

    @Test
    fun `a question with a real 33IQ title keeps it as a first line`() {
        val text = questionAsPlainText(detail(title = "浴室谜案"))

        text.lines().first() shouldBeEqualTo "浴室谜案"
    }

    @Test
    fun `a question without a title is not given a cut-off copy of its own body`() {
        val text = questionAsPlainText(detail())

        // The body appears once, as itself - not again as a truncated heading above it.
        text.lines().first() shouldBeEqualTo "男子被发现死在自家浴室。"
    }

    @Test
    fun `an open question copies its body without an empty choice block`() {
        val text = questionAsPlainText(detail(questionType = QuestionType.OPEN, choices = emptyList()))

        text shouldContain "男子被发现死在自家浴室。"
        text shouldNotContain "A."
        // No choices means the block is skipped entirely, not left as trailing blank lines.
        text shouldBeEqualTo "男子被发现死在自家浴室。\n\n第二段正文。"
    }

    private fun detail(
        title: String? = null,
        questionType: QuestionType = QuestionType.CHOICE,
        choices: List<Choice> = listOf(Choice("A", "冰块"), Choice("B", "毛巾冰棒")),
    ) = QuestionDetail(
        id = 590_073,
        title = title,
        bodyText = "男子被发现死在自家浴室。\n\n第二段正文。",
        imageUrls = listOf("https://a.33iq.com/upload/26/09/03/images/1788.jpg"),
        tags = listOf("侦探推理"),
        breadcrumb = emptyList(),
        author = "啊嘟嘟飛",
        publishedDate = "2026-09-03",
        upvoteCount = 5,
        isUpvoted = false,
        commentCount = 23,
        collectCount = 2,
        rightRatio = 58,
        questionType = questionType,
        choices = choices,
        analysis = null,
        sourceUrl = "https://www.33iq.com/question/590073.html",
    )
}
