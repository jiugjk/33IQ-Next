package com.jiugjk.iq33.feature.feed.data.repository

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
    private val sut = QuestionProgressRepositoryImpl(preferences, manager)

    @Test
    fun `answered IDs and the filter survive repository recreation`() {
        sut.recordAnswered(42, "uid:1")
        sut.recordAnswered(42, "uid:1")
        sut.setHideAnswered(true)

        val restored = QuestionProgressRepositoryImpl(preferences, manager)
        restored.current.answeredIds shouldBeEqualTo setOf(42L)
        restored.current.hideAnswered shouldBeEqualTo true
    }

    @Test
    fun `viewed answer restrictions persist separately and respect account isolation`() {
        sut.recordAnswerViewed(42, "uid:1")
        sut.recordAnswered(43, "uid:1")
        sut.recordAnswerViewed(43, "uid:1")
        val restored = QuestionProgressRepositoryImpl(preferences, manager)
        restored.current.viewedAnswerIds shouldBeEqualTo setOf(42L, 43L)
        restored.current.answeredIds shouldBeEqualTo setOf(43L)
        restored.current.isSubmissionBlocked(42) shouldBeEqualTo true
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
        restored.recordAnswerViewed(44, "uid:1")
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
        session.value = IqSession(SessionStatus.GUEST)
        restored.recordAnswerViewed(45, null)
        restored.current.viewedAnswerIds shouldBeEqualTo emptySet()
    }

    @Test
    fun `pending analysis state survives recreation and failed writes preserve the previous latch`() {
        preferences.failNextCommit = true
        sut.setAnswerRevealPending(42, true, "uid:1") shouldBeEqualTo false
        sut.current.pendingAnswerRevealIds shouldBeEqualTo emptySet()
        sut.setAnswerRevealPending(42, true, "uid:1") shouldBeEqualTo true
        val restored = QuestionProgressRepositoryImpl(preferences, manager)
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
        sut.recordAnswered(42, "uid:1")
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        sut.recordAnswered(43, "uid:1")
        sut.current.answeredIds shouldBeEqualTo emptySet()
        sut.recordAnswered(44, "uid:2")
        session.value = IqSession(SessionStatus.GUEST)
        sut.recordAnswered(45, null)
        sut.current.answeredIds shouldBeEqualTo emptySet()
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:1")
        sut.current.answeredIds shouldBeEqualTo setOf(42L)
    }

    @Test
    fun `cursor and ordered recent IDs survive restart and are isolated by category and account`() {
        val position = FeedPosition("https://www.33iq.com/question/?cursor=abc", linkedSetOf(30, 2, 11))
        sut.saveFeedPosition("all", position, "uid:1")
        val restored = QuestionProgressRepositoryImpl(preferences, manager)
        restored.feedPosition("all") shouldBeEqualTo position
        restored.feedPosition("all").lastQuestionIds.toList() shouldBeEqualTo listOf(30L, 2L, 11L)
        restored.feedPosition("logic") shouldBeEqualTo FeedPosition()
        session.value = IqSession(SessionStatus.AUTHENTICATED, accountKey = "uid:2")
        restored.feedPosition("all") shouldBeEqualTo FeedPosition()
        restored.saveFeedPosition("all", position, "uid:1")
        restored.feedPosition("all") shouldBeEqualTo FeedPosition()
    }

    @Test
    fun `answering and toggling the filter publish progress immediately`() =
        runTest {
            val updates = mutableListOf<Set<Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                sut.progress.collect { updates += it.answeredIds }
            }
            sut.recordAnswered(42, "uid:1")
            sut.setHideAnswered(true)
            updates.last() shouldBeEqualTo setOf(42L)
        }
}
