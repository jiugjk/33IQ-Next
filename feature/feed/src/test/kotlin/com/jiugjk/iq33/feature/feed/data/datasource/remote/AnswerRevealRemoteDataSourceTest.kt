package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.QuestionContentBlock
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerRevealRemoteDataSourceTest {
    private val client = mockk<IqHtmlClient>()
    private val sut = AnswerRevealRemoteDataSource(client, UnconfinedTestDispatcher())

    @Test
    fun `captured paid quote is read from pay not hardcoded or multiplied again`() =
        runTest {
            coEvery { client.postWebFormForText(IqConstants.ANSWER_QUOTE_URL, any()) } returns
                """{"status":"success","type":"putong","pay":"60","multiple":"1.1","isLimit":"0"}"""
            sut.quote(475_903) shouldBeEqualTo AnswerQuote(475_903, 60, true)
            coVerify(exactly = 0) { client.postWebFormForText(IqConstants.ANSWER_PAYMENT_URL, any()) }
            coVerify(exactly = 0) { client.postWebFormForText(IqConstants.ANSWER_REVEAL_URL, any()) }
            coVerify {
                client.postWebFormForText(
                    IqConstants.ANSWER_QUOTE_URL,
                    match {
                        it["q_id"] == "475903" && it["lot_number"] == "default" && it["pass_token"] == "default"
                    },
                )
            }
        }

    @Test
    fun `show permission and zero priced paid authorisation remain distinct`() =
        runTest {
            coEvery { client.postWebFormForText(any(), any()) } returns """{"status":"show"}"""
            sut.quote(1) shouldBeEqualTo AnswerQuote(1, 0, false)
            coEvery { client.postWebFormForText(any(), any()) } returns """{"status":"success","pay":"0"}"""
            sut.quote(1) shouldBeEqualTo AnswerQuote(1, 0, true)
        }

    @Test
    fun `exhausting the free allowance does not suppress a valid paid quote`() =
        runTest {
            coEvery { client.postWebFormForText(any(), any()) } returns """{"status":"success","pay":"30","isLimit":"1"}"""
            sut.quote(1).cost shouldBeEqualTo 30
        }

    @Test
    fun `missing negative malformed and unknown prices are never advertised as free`() =
        runTest {
            listOf(
                """{"status":"success"}""",
                """{"status":"success","pay":"-1"}""",
                """{"status":"success","pay":"abc"}""",
                """{"status":"guest"}""",
                """{"status":"captcha"}""",
                """{"status":"mystery","pay":"0"}""",
                "<html>verification</html>",
            ).forEach { body ->
                coEvery { client.postWebFormForText(any(), any()) } returns body
                runCatching { sut.quote(1) }.exceptionOrNull() shouldBeInstanceOf IqResponseException::class
            }
        }

    @Test
    fun `payment and reveal both require explicit success`() =
        runTest {
            listOf("scoreover", "limit", "guest", "silent", "notpaid", "error").forEach { status ->
                coEvery { client.postWebFormForText(any(), any()) } returns """{"status":"$status"}"""
                runCatching { sut.pay(1) }.exceptionOrNull() shouldBeInstanceOf IqResponseException::class
                runCatching { sut.fetch(1) }.exceptionOrNull() shouldBeInstanceOf IqResponseException::class
            }
        }

    @Test
    fun `answer explanation paragraphs and images survive the rich content parse`() =
        runTest {
            coEvery { client.postWebFormForText(IqConstants.ANSWER_REVEAL_URL, any()) } returns
                """{"status":"success","answer":"A","explanation":"<p>第一段</p><img src='/upload/diagram.png'><p>第二段</p>"}"""
            val reveal = sut.fetch(475_903)
            reveal.answerText shouldBeEqualTo "A"
            reveal.explanationText shouldBeEqualTo "第一段\n\n第二段"
            reveal.explanationBlocks shouldBeEqualTo
                listOf(
                    QuestionContentBlock.Text("第一段"),
                    QuestionContentBlock.Image("https://www.33iq.com/upload/diagram.png"),
                    QuestionContentBlock.Text("第二段"),
                )
            coVerify {
                client.postWebFormForText(
                    IqConstants.ANSWER_REVEAL_URL,
                    match {
                        it["q_id"] == "475903" && it["randstr"].orEmpty().startsWith("35yaobeicaiyuan")
                    },
                )
            }
        }

    @Test
    fun `missing or empty reveal payload is a failure not a blank successful purchase`() =
        runTest {
            listOf(
                """{"status":"success"}""",
                """{"status":"success","answer":"","explanation":"<p></p>"}""",
                """{"status":"success","answer":false,"explanation":"text"}""",
            ).forEach { body ->
                coEvery { client.postWebFormForText(any(), any()) } returns body
                runCatching { sut.fetch(1) }.exceptionOrNull() shouldBeInstanceOf IqResponseException::class
            }
        }
}
