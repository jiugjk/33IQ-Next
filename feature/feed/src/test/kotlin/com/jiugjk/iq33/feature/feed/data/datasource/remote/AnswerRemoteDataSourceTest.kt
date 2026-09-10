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
import java.io.IOException

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
            (error as IqResponseException).afterSideEffect shouldBeEqualTo true
        }

    @Test
    fun `a dropped connection after showtips is an unknown paid outcome`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } throws IOException("broken pipe")

            val error = runCatching { sut.revealHint(1) }.exceptionOrNull() as IqResponseException

            error.afterSideEffect shouldBeEqualTo true
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
