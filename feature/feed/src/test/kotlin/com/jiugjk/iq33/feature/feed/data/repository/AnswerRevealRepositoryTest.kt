package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRevealRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.datasource.remote.IqResponseException
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.IOException

class AnswerRevealRepositoryTest {
    private val preferences = FakeSharedPreferences()
    private val session = MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:1"))
    private val manager = mockk<SessionManager> { every { sessionFlow } returns session }
    private val progress = QuestionProgressRepositoryImpl(preferences, manager)
    private val remote = mockk<AnswerRevealRemoteDataSource>()
    private val sut = AnswerRevealRepositoryImpl(remote, progress)
    private val quote = AnswerQuote(1, 60, true)
    private val reveal = AnswerReveal("A", "解析")

    @Test
    fun `payment follows a fresh matching quote and success records viewed analysis not answered`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } coAnswers {
                progress.current.pendingAnswerRevealIds shouldBeEqualTo setOf(1L)
            }
            coEvery { remote.fetch(1) } returns reveal
            sut.reveal(quote) shouldBeEqualTo Result.Success(reveal)
            coVerifyOrder {
                remote.quote(1)
                remote.pay(1)
                remote.fetch(1)
            }
            progress.current.viewedAnswerIds shouldBeEqualTo setOf(1L)
            progress.current.answeredIds shouldBeEqualTo emptySet()
            progress.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
        }

    @Test
    fun `free permission never calls payment`() =
        runTest {
            val free = AnswerQuote(1, 0, false)
            coEvery { remote.quote(1) } returns free
            coEvery { remote.fetch(1) } returns reveal
            sut.reveal(free) shouldBeEqualTo Result.Success(reveal)
            coVerify(exactly = 0) { remote.pay(any()) }
        }

    @Test
    fun `changed price or a newly required payment needs fresh user consent`() =
        runTest {
            coEvery { remote.quote(1) } returns AnswerQuote(1, 61, true)
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo false
            coEvery { remote.quote(1) } returns AnswerQuote(1, 0, true)
            (sut.reveal(AnswerQuote(1, 0, false)) as Result.Failure).afterSideEffect shouldBeEqualTo false
            coVerify(exactly = 0) { remote.pay(any()) }
            coVerify(exactly = 0) { remote.fetch(any()) }
            progress.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
        }

    @Test
    fun `failure to persist the pending latch prevents payment and reveal`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            preferences.failNextCommit = true
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo false
            coVerify(exactly = 0) { remote.pay(any()) }
            coVerify(exactly = 0) { remote.fetch(any()) }
        }

    @Test
    fun `insufficient score releases the latch and never fetches or marks viewed`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } throws IqResponseException("payment", IqResponseException.Reason.BUSINESS_ERROR, "scoreover")
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo false
            coVerify(exactly = 0) { remote.fetch(any()) }
            progress.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
            progress.current.viewedAnswerIds shouldBeEqualTo emptySet()
        }

    @Test
    fun `failure after payment survives repository recreation and recovery cannot pay again`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } returns Unit
            coEvery { remote.fetch(1) } throws IOException("response lost")
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo true
            progress.current.pendingAnswerRevealIds shouldBeEqualTo setOf(1L)
            progress.current.viewedAnswerIds shouldBeEqualTo emptySet()
            val restoredProgress = QuestionProgressRepositoryImpl(preferences, manager)
            val restored = AnswerRevealRepositoryImpl(remote, restoredProgress)
            coEvery { remote.fetch(1) } returns reveal
            restored.recover(1) shouldBeEqualTo Result.Success(reveal)
            coVerify(exactly = 1) { remote.pay(1) }
            coVerify(exactly = 1) { remote.quote(1) }
            restoredProgress.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
            restoredProgress.current.viewedAnswerIds shouldBeEqualTo setOf(1L)
        }

    @Test
    fun `timeout during payment keeps recovery only mode even if the old dialog is confirmed again`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } throws IOException("timeout")
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo true
            coEvery { remote.fetch(1) } throws IqResponseException("reveal", IqResponseException.Reason.BUSINESS_ERROR, "notpaid")
            (sut.reveal(quote) as Result.Failure).afterSideEffect shouldBeEqualTo true
            coVerify(exactly = 1) { remote.pay(1) }
            progress.current.pendingAnswerRevealIds shouldBeEqualTo setOf(1L)
        }

    @Test
    fun `leaving during payment does not discard the durable recovery latch`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } coAnswers {
                started.complete(Unit)
                awaitCancellation()
            }
            val job = launch { sut.reveal(quote) }
            started.await()
            job.cancelAndJoin()
            val restored = QuestionProgressRepositoryImpl(preferences, manager)
            restored.current.pendingAnswerRevealIds shouldBeEqualTo setOf(1L)
            restored.current.isSubmissionBlocked(1) shouldBeEqualTo true
            coVerify(exactly = 0) { remote.fetch(any()) }
        }

    @Test
    fun `concurrent confirmations cannot send overlapping payments`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } coAnswers {
                started.complete(Unit)
                awaitCancellation()
            }
            val first = launch { sut.reveal(quote) }
            started.await()
            (sut.reveal(quote) is Result.Failure) shouldBeEqualTo true
            coVerify(exactly = 1) { remote.pay(any()) }
            first.cancelAndJoin()
        }

    @Test
    fun `switching accounts after a charge never fetches with or updates the other account`() =
        runTest {
            coEvery { remote.quote(1) } returns quote
            coEvery { remote.pay(1) } coAnswers { session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2") }
            runCatching { sut.reveal(quote) }
            coVerify(exactly = 0) { remote.fetch(any()) }
            progress.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
            progress.current.viewedAnswerIds shouldBeEqualTo emptySet()
            session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:1")
            progress.current.pendingAnswerRevealIds shouldBeEqualTo setOf(1L)
        }
}
