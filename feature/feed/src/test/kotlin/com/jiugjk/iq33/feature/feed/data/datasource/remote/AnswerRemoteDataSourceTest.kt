package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.library.network.IqHtmlClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerRemoteDataSourceTest {
    private val htmlClient = mockk<IqHtmlClient>()
    private val sut = AnswerRemoteDataSource(htmlClient, UnconfinedTestDispatcher())

    @Test
    fun `an error envelope from praise is rejected instead of becoming an upvote count of zero`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"error"}"""

            val error = runCatching { sut.praiseQuestion(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `praise returns the server-reported new count`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success","num":"102"}"""

            sut.praiseQuestion(1) shouldBeEqualTo 102
        }

    @Test
    fun `a hint quote without a usable price is rejected instead of quoting zero`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success"}"""

            val error = runCatching { sut.quoteHint(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `a hint quote uses the tier the account is actually on`() =
        runTest {
            coEvery {
                htmlClient.postFormForText(any(), any())
            } returns """{"answerpay":"10","memberpay":"6","lifeMemberpay":"3","paytype":"memberpay"}"""

            val quote = sut.quoteHint(1)

            quote.effectiveCost shouldBeEqualTo 6
            quote.normalCost shouldBeEqualTo 10
        }

    @Test
    fun `a hint with no text is rejected instead of revealing an empty hint`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success"}"""

            val error = runCatching { sut.revealHint(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `revealing an answer stops at the first failing step instead of reporting an empty answer`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"error"}"""

            val error = runCatching { sut.revealAnswer(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `a failure after the first step is flagged as possibly already charged`() =
        runTest {
            var call = 0
            coEvery { htmlClient.postFormForText(any(), any()) } coAnswers {
                call++
                if (call == 1) """{"answer":"A","explanation":"<p>解析</p>"}""" else """{"status":"error"}"""
            }

            val failure = runCatching { sut.revealAnswer(1) }.exceptionOrNull() as IqResponseException

            failure.afterSideEffect shouldBeEqualTo true
        }

    @Test
    fun `a successful reveal converts its explanation html to text`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returnsMany
                listOf(
                    """{"answer":"A","explanation":"<p>第一段</p><p>第二段</p>"}""",
                    """{"pay":"5"}""",
                    """{"status":"success"}""",
                )

            val reveal = sut.revealAnswer(1)

            reveal.answer shouldBeEqualTo "A"
            reveal.explanation shouldBeEqualTo "第一段\n\n第二段"
            reveal.cost shouldBeEqualTo 5
        }

    @Test
    fun `an answer only the last step returns is still revealed`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returnsMany
                listOf(
                    // showanswertrue reads as an eligibility check: "0" is the ordinary
                    // not-yet-unlocked state, not a refusal, and used to abort the whole flow.
                    """{"status":"0"}""",
                    """{"pay":"5"}""",
                    """{"status":"success","answer":"B","explanation":"<p>因为如此</p>"}""",
                )

            val reveal = sut.revealAnswer(1)

            reveal.answer shouldBeEqualTo "B"
            reveal.explanation shouldBeEqualTo "因为如此"
            reveal.cost shouldBeEqualTo 5
        }

    @Test
    fun `a reveal where no step returns an answer fails instead of revealing an empty one`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success"}"""

            val error = runCatching { sut.revealAnswer(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `a reveal refused because nobody is signed in is reported as a failure`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"guest"}"""

            val error = runCatching { sut.revealAnswer(1) }.exceptionOrNull()

            error shouldBeInstanceOf IqResponseException::class
        }

    @Test
    fun `an unreported score change is null rather than zero`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success"}"""

            sut.submitAnswer(1, "A") shouldBeEqualTo SubmitAnswerResult.Correct(scoreDelta = null, myScore = null)
        }

    @Test
    fun `a repeated submission is reported as already answered`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"repeat"}"""

            sut.submitAnswer(1, "A") shouldBeEqualTo SubmitAnswerResult.AlreadyAnswered
        }
}
