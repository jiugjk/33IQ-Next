package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test

class QuestionJsonParserTest {
    private val sut = QuestionJsonParser()

    @Test
    fun `the full body is kept even when a short title exists`() {
        val body = "甲".repeat(100)
        val detail = sut.parseQuestionDetail(payload(title = "逻辑题", context = "<p>$body</p>"), id = 42)

        detail?.title shouldBeEqualTo "逻辑题"
        detail?.bodyText shouldBeEqualTo body
    }

    @Test
    fun `an empty title stays absent instead of becoming a cut-off copy of the body`() {
        val body = "乙".repeat(100)
        val detail = sut.parseQuestionDetail(payload(title = "", context = "<p>$body</p>"), id = 42)

        detail?.title.shouldBeNull()
        detail?.bodyText shouldBeEqualTo body
        // Places that genuinely need one line still get the site's own truncation-style label.
        detail?.shortLabel shouldBeEqualTo "乙".repeat(60)
    }

    @Test
    fun `an image-only question still carries its image`() {
        val detail = sut.parseQuestionDetail(payload(title = "看图", context = """<img src="/upload/q.png">"""), id = 7)

        detail?.imageUrls shouldBeEqualTo listOf("https://www.33iq.com/upload/q.png")
    }

    @Test
    fun `a body thumbnail is upgraded to the original upload`() {
        val thumbnail = """<img src="https://a.33iq.com/upload/26/09/03/_thumbs/1788.jpg!33.jpg">"""
        val detail = sut.parseQuestionDetail(payload(title = "", context = thumbnail), id = 7)

        detail?.imageUrls shouldBeEqualTo listOf("https://a.33iq.com/upload/26/09/03/images/1788.jpg")
    }

    @Test
    fun `a question whose body embeds no image falls back to the payload's own pic field`() {
        val detail =
            sut.parseQuestionDetail(
                payload(title = "", context = "正文", pic = "https://a.33iq.com/upload/26/09/03/images/1788.jpg"),
                id = 7,
            )

        detail?.imageUrls shouldBeEqualTo listOf("https://a.33iq.com/upload/26/09/03/images/1788.jpg")
    }

    @Test
    fun `the source url is the public page, not the json api url`() {
        val detail = sut.parseQuestionDetail(payload(title = "题", context = "正文"), id = 12_345)

        detail?.sourceUrl shouldBeEqualTo "https://www.33iq.com/question/12345.html"
        detail?.sourceUrl?.shouldContain("question")
    }

    @Test
    fun `choices and type are read from the payload`() {
        val detail = sut.parseQuestionDetail(payload(title = "题", context = "正文"), id = 1)

        detail?.questionType shouldBeEqualTo QuestionType.CHOICE
        detail?.choices?.map { it.id } shouldBeEqualTo listOf("A", "B")
    }

    @Test
    fun `an unexpected response shape is reported as null rather than a blank question`() {
        sut.parseQuestionDetail("""{"status":"error"}""", id = 1).shouldBeNull()
    }

    private fun payload(
        title: String,
        context: String,
        pic: String = "",
    ) = buildJsonArray {
        addJsonObject {
            put("qc_title", title)
            put("qc_context", context)
            put("pic", pic)
            put("ischoose", "1")
            put("qc_choose_a", "选项一")
            put("qc_choose_b", "选项二")
            put("praise", "10")
            put("isPraise", "0")
            put("o_commentnum", "3")
            put("o_collectnum", "2")
            put("username", "作者")
            put("ctime", "2024-01-02 03:04:05")
        }
    }.toString()
}
