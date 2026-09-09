package com.jiugjk.iq33.feature.feed.data.datasource.remote

import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class HtmlTextTest {
    @Test
    fun `paragraphs are separated and line breaks inside one are kept`() {
        val text = htmlToPlainText("<p>甲<br>乙</p><p>丙</p>")

        text shouldBeEqualTo listOf("甲\n乙", "丙").joinToString(PARAGRAPH_SEPARATOR)
    }

    @Test
    fun `text of a block that also has block children is not lost`() {
        val text = htmlToPlainText("<div>前言<p>正文</p></div>")

        text shouldBeEqualTo listOf("前言", "正文").joinToString(PARAGRAPH_SEPARATOR)
    }

    @Test
    fun `list items and table cells each become their own paragraph`() {
        val text = htmlToPlainText("<ul><li>一</li><li>二</li></ul>")

        text shouldBeEqualTo listOf("一", "二").joinToString(PARAGRAPH_SEPARATOR)
    }

    @Test
    fun `entities are decoded and runs of whitespace collapse`() {
        val text = htmlToPlainText("<p>甲&nbsp;&nbsp; 乙   丙</p>")

        text shouldBeEqualTo "甲 乙 丙"
    }

    @Test
    fun `text with no block markup at all still survives`() {
        htmlToPlainText("裸文本") shouldBeEqualTo "裸文本"
    }

    @Test
    fun `blank input produces empty text`() {
        htmlToPlainText("   ").shouldBeEmpty()
    }

    @Test
    fun `image sources are resolved against the site base url`() {
        val images = htmlImageUrls("""<p>题目</p><img src="/upload/a.png"><img src="https://a.33iq.com/b.png">""", "https://www.33iq.com")

        images shouldBeEqualTo listOf("https://www.33iq.com/upload/a.png", "https://a.33iq.com/b.png")
    }

    @Test
    fun `body thumbnails are upgraded to the original upload`() {
        val images =
            htmlImageUrls(
                """<img src="https://a.33iq.com/upload/26/09/03/_thumbs/1788.jpg!33.jpg">""" +
                    """<img src="https://a.33iq.com/upload/26/09/03/_thumbs/big/1789.jpg">""",
                "https://www.33iq.com",
            )

        images shouldBeEqualTo
            listOf(
                "https://a.33iq.com/upload/26/09/03/images/1788.jpg",
                "https://a.33iq.com/upload/26/09/03/images/1789.jpg",
            )
    }

    @Test
    fun `the two renditions of one upload collapse to a single full-size image`() {
        val images =
            htmlImageUrls(
                """<img src="https://a.33iq.com/upload/a/_thumbs/1.jpg!33.jpg">""" +
                    """<img src="https://a.33iq.com/upload/a/images/1.jpg">""",
                "https://www.33iq.com",
            )

        images shouldBeEqualTo listOf("https://a.33iq.com/upload/a/images/1.jpg")
    }

    @Test
    fun `an image url with neither downscaler is left alone`() {
        val url = "https://www.33iq.com/upload/a/plain.png"

        fullSizeImageUrl(url) shouldBeEqualTo url
    }

    private companion object {
        const val PARAGRAPH_SEPARATOR = "\n\n"
    }
}
