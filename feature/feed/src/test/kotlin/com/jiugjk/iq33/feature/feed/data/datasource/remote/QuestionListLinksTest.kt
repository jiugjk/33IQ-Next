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

    @Test
    fun `captured tag navigation advances one page instead of jumping to page 932`() {
        // Reduced from the saved tag_tuili.html: its trailing > link is the LAST page.
        val tag = "https://www.33iq.com/tag/%D5%EC%CC%BD%CD%C6%C0%ED"
        val document =
            Jsoup.parse(
                "<div class='pagination pagination-right'><ul>" +
                    "<li class='disabled'><a href='$tag.html'>&lt;</a></li>" +
                    "<li class='active'><a href='#'>1</a></li>" +
                    "<li><a href='$tag/2.html'>2</a></li>" +
                    "<li><a href='$tag/3.html'>3</a></li>" +
                    "<li><a href='$tag/932.html'>&gt;</a></li></ul></div>",
                "$tag.html",
            )
        QuestionListLinks.nextPage(document) shouldBeEqualTo "$tag/2.html"
    }

    @Test
    fun `declared next link has priority over a numbered sibling`() {
        next(
            "<link rel='next' href='?cursor=next'>" +
                "<ul class='pagination'><li class='active'>1</li><li><a href='?page=2'>2</a></li></ul>",
        ) shouldBeEqualTo "https://www.33iq.com/question/?cursor=next"
    }

    @Test
    fun `legacy lists without navigation advance page numbers on the same path`() {
        legacy("https://www.33iq.com/question/") shouldBeEqualTo "https://www.33iq.com/question/?page=2"
        legacy("https://www.33iq.com/question/?page=2") shouldBeEqualTo "https://www.33iq.com/question/?page=3"
        legacy("https://www.33iq.com/tag/logic.html?page=39#list") shouldBeEqualTo
            "https://www.33iq.com/tag/logic.html?page=40"
    }

    @Test
    fun `explicit pagination never falls back including disabled or unsafe next links`() {
        listOf(
            "<div class='pagination'><a href='?page=1'>上一页</a></div>",
            "<a rel='next' class='disabled' href='?page=2'>Next</a>",
            "<link rel='next' href='https://evil.example/question/'>",
            "<div class='pager'></div>",
        ).forEach { html ->
            QuestionListLinks.legacyNextPage(Jsoup.parse(html, "https://www.33iq.com/question/")) shouldBeEqualTo null
        }
    }

    @Test
    fun `legacy paging rejects unsafe URLs other cursor protocols and invalid page numbers`() {
        listOf(
            "https://evil.example/question/",
            "http://www.33iq.com/question/",
            "https://www.33iq.com/question/123.html",
            "https://www.33iq.com/question/?cursor=abc",
            "https://www.33iq.com/question/?page=0",
            "https://www.33iq.com/question/?page=-1",
            "https://www.33iq.com/question/?page=invalid",
            "https://www.33iq.com/question/?page=2147483647",
            "https://www.33iq.com/question/?page=1&page=2",
        ).forEach { url -> legacy(url) shouldBeEqualTo null }
    }

    private fun legacy(url: String) = QuestionListLinks.legacyNextPage(Jsoup.parse("", url))

    private fun next(html: String) = QuestionListLinks.nextPage(Jsoup.parse(html, "https://www.33iq.com/question/"))
}
