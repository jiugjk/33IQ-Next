package com.jiugjk.iq33.feature.feed.data.datasource.remote

import org.amshove.kluent.shouldBeEqualTo
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class QuestionListLinksTest {
    @Test
    fun `relative next link is resolved without guessing a page parameter`() {
        next("<a rel='next' href='?cursor=abc&amp;order=new#list'>Next</a>") shouldBeEqualTo
            "https://www.33iq.com/question/?cursor=abc&order=new"
    }

    @Test
    fun `Chinese pagination and active page sibling are supported`() {
        next("<div class='pagination'><a href='?offset=20'>下一页</a></div>") shouldBeEqualTo
            "https://www.33iq.com/question/?offset=20"
        next("<ul class='pagination'><li class='active'>1</li><li><a href='?offset=20'>2</a></li></ul>") shouldBeEqualTo
            "https://www.33iq.com/question/?offset=20"
    }

    @Test
    fun `disabled missing and self links mean no next page`() {
        next("<div class='pagination'><li class='disabled'><a href='?offset=20'>下一页</a></li></div>") shouldBeEqualTo null
        next("<a rel='next' href='#list'>Next</a>") shouldBeEqualTo null
        next("<a rel='next'>Next</a>") shouldBeEqualTo null
        next("<div class='pagination'><a href='?offset=0'>上一页</a></div>") shouldBeEqualTo null
    }

    @Test
    fun `external hosts credentials cleartext detail and action links are rejected`() {
        listOf(
            "https://evil.example/question/?page=2",
            "https://www.33iq.com.evil.example/question/",
            "http://www.33iq.com/question/?page=2",
            "https://user@www.33iq.com/question/",
            "https://www.33iq.com:444/question/",
            "https://www.33iq.com/question/123.html",
            "https://www.33iq.com/user/logout/",
            "javascript:alert(1)",
        ).forEach { url ->
            QuestionListLinks.isSafeListUrl(url) shouldBeEqualTo false
            next("<a rel='next' href='$url'>Next</a>") shouldBeEqualTo null
        }
    }

    @Test
    fun `tag pagination stays on its advertised path`() {
        val document = Jsoup.parse("<link rel='next' href='?offset=20'>", "https://www.33iq.com/tag/logic/")
        QuestionListLinks.nextPage(document) shouldBeEqualTo "https://www.33iq.com/tag/logic/?offset=20"
    }

    private fun next(html: String) = QuestionListLinks.nextPage(Jsoup.parse(html, "https://www.33iq.com/question/"))
}
