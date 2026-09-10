package com.jiugjk.iq33.feature.feed.data.datasource.remote

import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class QuestionHtmlParserTest {
    private val sut = QuestionHtmlParser()

    @Test
    fun `a recognised empty list page is empty rather than a parse failure`() {
        val document = Jsoup.parse("<html><head><title>精选题目</title></head><body><div class='pagination'></div></body></html>")

        sut.parseQuestionSummaries(document).shouldBeEmpty()
    }

    @Test
    fun `an unrelated page is not reported as an empty list`() {
        val document = Jsoup.parse("<html><head><title>出错了</title></head><body><p>维护中</p></body></html>")

        val error = runCatching { sut.parseQuestionSummaries(document) }.exceptionOrNull()

        error shouldBeInstanceOf UnexpectedPageException::class
    }

    @Test
    @Suppress("MaxLineLength")
    fun `question nodes are parsed`() {
        val html =
            """<div class="linktopic" itemtype="https://schema.org/Question"><div class="title"><a href="/question/12.html">标题</a></div><div class="info"><span>3 点赞 1 评论</span></div></div>"""

        val questions = sut.parseQuestionSummaries(Jsoup.parse(html))

        questions.map { it.id } shouldBeEqualTo listOf(12L)
        questions.single().title shouldBeEqualTo "标题"
    }
}
