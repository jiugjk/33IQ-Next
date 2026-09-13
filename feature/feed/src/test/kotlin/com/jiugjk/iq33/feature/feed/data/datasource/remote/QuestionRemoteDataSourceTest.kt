package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.library.network.IqHtmlClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionRemoteDataSourceTest {
    private val htmlClient = mockk<IqHtmlClient>()
    private val sut = QuestionRemoteDataSource(htmlClient, QuestionHtmlParser(), QuestionJsonParser(), UnconfinedTestDispatcher())

    @Test
    fun `lists without next controls continue beyond page two and end on an empty page`() =
        runTest {
            coEvery { htmlClient.get(any()) } answers {
                val url = firstArg<String>()
                val page = url.substringAfter("page=", "1").toInt()
                Jsoup.parse(listHtml(page.takeIf { it <= 4 }?.toLong()), url)
            }
            var cursor: String? = null
            repeat(4) { index ->
                val page = sut.fetchQuestionList(Category.ALL, cursor)
                page.questions.single().id shouldBeEqualTo index + 1L
                page.isNextPageInferred shouldBeEqualTo true
                cursor = page.nextPageUrl
                cursor shouldBeEqualTo "$LIST_URL?page=${index + 2}"
            }
            val end = sut.fetchQuestionList(Category.ALL, cursor)
            end.questions shouldBeEqualTo emptyList()
            end.nextPageUrl shouldBeEqualTo null
            end.isNextPageInferred shouldBeEqualTo false
            coVerify(exactly = 1) { htmlClient.get(LIST_URL) }
        }

    @Test
    fun `an advertised cursor takes precedence over legacy page numbers`() =
        runTest {
            coEvery { htmlClient.get(LIST_URL) } returns
                Jsoup.parse(listHtml(1) + "<a rel='next' href='?offset=20'>Next</a>", LIST_URL)
            val page = sut.fetchQuestionList(Category.ALL, null)
            page.nextPageUrl shouldBeEqualTo "$LIST_URL?offset=20"
            page.isNextPageInferred shouldBeEqualTo false
        }

    @Test
    fun `featured list pagination continues onto the 24h feed`() =
        runTest {
            coEvery { htmlClient.get(LIST_URL) } returns
                Jsoup.parse(
                    listHtml(1) +
                        "<div class='pagination'><ul>" +
                        "<li class='active'><a href='#'>1</a></li>" +
                        "<li><a href='/24h/2.html'>2</a></li></ul></div>",
                    LIST_URL,
                )
            coEvery { htmlClient.get("https://www.33iq.com/24h/2.html") } returns
                Jsoup.parse(
                    listHtml(2) +
                        "<div class='pagination'><ul>" +
                        "<li class='active'><a href='#'>2</a></li>" +
                        "<li><a href='/24h/3.html'>3</a></li></ul></div>",
                    "https://www.33iq.com/24h/2.html",
                )
            val first = sut.fetchQuestionList(Category.ALL, null)
            first.nextPageUrl shouldBeEqualTo "https://www.33iq.com/24h/2.html"
            first.isNextPageInferred shouldBeEqualTo false
            val second = sut.fetchQuestionList(Category.FEATURED, first.nextPageUrl)
            second.questions.single().id shouldBeEqualTo 2L
            second.nextPageUrl shouldBeEqualTo "https://www.33iq.com/24h/3.html"
        }

    @Test
    fun `an explicit last page does not use legacy paging`() =
        runTest {
            coEvery { htmlClient.get(LIST_URL) } returns
                Jsoup.parse(listHtml(1) + "<div class='pagination'><span class='disabled'>下一页</span></div>", LIST_URL)
            sut.fetchQuestionList(Category.ALL, null).nextPageUrl shouldBeEqualTo null
        }

    @Test
    fun `tag legacy paging preserves the requested category path`() =
        runTest {
            val category = Category.DEFAULT_CATEGORIES.first { it.id == "logic" }
            coEvery { htmlClient.get(any()) } answers { Jsoup.parse(listHtml(1), firstArg<String>()) }
            val first = sut.fetchQuestionList(category, null)
            val next = requireNotNull(first.nextPageUrl)
            next.startsWith("https://www.33iq.com/tag/") shouldBeEqualTo true
            next.endsWith(".html?page=2") shouldBeEqualTo true
            sut.fetchQuestionList(category, next).nextPageUrl shouldBeEqualTo next.replace("page=2", "page=3")
            coVerify(exactly = 1) { htmlClient.get(next) }
        }

    @Test
    fun `a security challenge remains a failure not an empty or continuing page`() =
        runTest {
            coEvery { htmlClient.get(LIST_URL) } returns Jsoup.parse("<title>安全验证</title>", LIST_URL)
            runCatching { sut.fetchQuestionList(Category.ALL, null) }.exceptionOrNull() shouldBeInstanceOf UnexpectedPageException::class
        }

    // Synthetic list fixture: deliberately no pagination controls, as on the reported page.
    private fun listHtml(id: Long?): String =
        "<title>题目</title>" +
            if (id == null) {
                ""
            } else {
                "<div class='linktopic' itemtype='https://schema.org/Question'>" +
                    "<div class='title'><a href='/question/$id.html'>题 $id</a></div></div>"
            }

    private companion object {
        const val LIST_URL = "https://www.33iq.com/question/"
    }
}
