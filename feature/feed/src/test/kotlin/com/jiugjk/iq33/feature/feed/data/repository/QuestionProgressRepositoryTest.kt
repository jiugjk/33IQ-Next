package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionProgressRepositoryTest {
    private val preferences = FakeSharedPreferences()
    private val session = MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:1"))
    private val manager = mockk<SessionManager> { every { sessionFlow } returns session }
    private val answerRecords = InMemoryAnswerRecordRepository { session.value.accountKey }
    private val sut = QuestionProgressRepositoryImpl(preferences, manager, answerRecords)

    @Test
    fun `answered IDs and the filter survive repository recreation`() {
        answerRecords.recordAnswer(accountKey = "uid:1", questionId = 42)
        answerRecords.recordAnswer(accountKey = "uid:1", questionId = 42)
        sut.setHideAnswered(true)

        val restored = QuestionProgressRepositoryImpl(preferences, manager, answerRecords)
        restored.current.answeredIds shouldBeEqualTo setOf(42L)
        restored.current.hideAnswered shouldBeEqualTo true
    }

    @Test
    fun `viewed answer restrictions persist separately and respect account isolation`() {
        answerRecords.recordExplanationViewed("uid:1", 42)
        answerRecords.recordAnswer(accountKey = "uid:1", questionId = 43)
        answerRecords.recordExplanationViewed("uid:1", 43)
        val restored = QuestionProgressRepositoryImpl(preferences, manager, answerRecords)
        restored.current.viewedAnswerIds shouldBeEqualTo setOf(42L, 43L)
        restored.current.answeredIds shouldBeEqualTo setOf(43L)
        restored.current.isSubmissionBlocked(42) shouldBeEqualTo true
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
        answerRecords.recordExplanationViewed("uid:1", 44)
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
        session.value = IqSession(SessionStatus.GUEST)
        answerRecords.recordExplanationViewed(null, 45)
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
    }

    @Test
    fun `explanation-only migration shape keeps answeredAt null`() {
        answerRecords.seed(
            AnswerRecord(
                questionId = 7,
                accountKey = "uid:1",
                viewedExplanation = true,
                answeredAt = null,
                updatedAt = 1L,
            ),
        )
        sut.current.viewedAnswerIds shouldBeEqualTo setOf(7L)
        sut.current.answeredIds shouldBeEqualTo emptySet()
        answerRecords.get("uid:1", 7)?.answeredAt shouldBeEqualTo null
    }

    @Test
    fun `pending analysis state survives recreation and failed writes preserve the previous latch`() {
        preferences.failNextCommit = true
        sut.setAnswerRevealPending(42, true, "uid:1") shouldBeEqualTo false
        sut.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
        sut.setAnswerRevealPending(42, true, "uid:1") shouldBeEqualTo true
        val restored = QuestionProgressRepositoryImpl(preferences, manager, answerRecords)
        restored.current.pendingAnswerRevealIds shouldBeEqualTo setOf(42L)
        preferences.failNextCommit = true
        restored.setAnswerRevealPending(42, false, "uid:1") shouldBeEqualTo false
        restored.current.pendingAnswerRevealIds shouldBeEqualTo setOf(42L)
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        restored.setAnswerRevealPending(42, false, "uid:1") shouldBeEqualTo false
        restored.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
    }

    @Test
    fun `account changes isolate records and reject stale responses`() {
        answerRecords.recordAnswer(accountKey = "uid:1", questionId = 42)
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        answerRecords.recordAnswer(accountKey = "uid:1", questionId = 43)
        sut.current.answeredIds shouldBeEqualTo emptySet()
        answerRecords.recordAnswer(accountKey = "uid:2", questionId = 44)
        session.value = IqSession(SessionStatus.GUEST)
        answerRecords.recordAnswer(accountKey = null, questionId = 45)
        sut.current.answeredIds shouldBeEqualTo emptySet()
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:1")
        sut.current.answeredIds shouldBeEqualTo setOf(42L)
    }

    @Test
    fun `cursor and ordered recent IDs survive restart and are isolated by category and account`() {
        val position = FeedPosition("https://www.33iq.com/question/?cursor=abc", linkedSetOf(30, 2, 11))
        sut.saveFeedPosition("all", position, "uid:1")
        val restored = QuestionProgressRepositoryImpl(preferences, manager, answerRecords)
        restored.feedPosition("all") shouldBeEqualTo position
        restored.feedPosition("all").lastQuestionIds.toList() shouldBeEqualTo listOf(30L, 2L, 11L)
        restored.feedPosition("logic") shouldBeEqualTo FeedPosition()
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        restored.feedPosition("all") shouldBeEqualTo FeedPosition()
        restored.saveFeedPosition("all", position, "uid:1")
        restored.feedPosition("all") shouldBeEqualTo FeedPosition()
    }

    @Test
    fun `clearFeedPosition drops cursor and recent ids`() {
        sut.saveFeedPosition("all", FeedPosition("https://next", linkedSetOf(1, 2)), "uid:1")
        sut.clearFeedPosition("all", "uid:1")
        sut.feedPosition("all") shouldBeEqualTo FeedPosition()
    }

    @Test
    fun `answering and toggling the filter publish progress immediately`() =
        runTest {
            val updates = mutableListOf<Set<Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                sut.progress.collect { updates += it.answeredIds }
            }
            answerRecords.recordAnswer(accountKey = "uid:1", questionId = 42)
            sut.setHideAnswered(true)
            updates.last() shouldBeEqualTo setOf(42L)
        }
}
