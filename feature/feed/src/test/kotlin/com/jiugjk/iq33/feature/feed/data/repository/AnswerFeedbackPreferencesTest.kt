package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

/**
 * 连对 is behaviour data, so it follows the account; animations and haptics are device settings and
 * deliberately do not.
 */
class AnswerFeedbackPreferencesTest {
    private val preferences = FakeSharedPreferences()
    private val session = MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1"))
    private val sessionManager = mockk<SessionManager> { every { sessionFlow } returns session }
    private val sut = AnswerFeedbackPreferencesImpl(preferences, sessionManager)

    @Test
    fun `a second account does not inherit the first accounts streak`() =
        runTest {
            sut.recordCorrect("uid:1") shouldBeEqualTo 1
            sut.recordCorrect("uid:1") shouldBeEqualTo 2

            // Session expires, then another account logs in - no logout button involved.
            session.value = IqSession(SessionStatus.GUEST)
            sut.currentStreak shouldBeEqualTo 0
            session.value = IqSession(SessionStatus.AUTHENTICATED, "0", "uid:2")

            sut.currentStreak shouldBeEqualTo 0
            sut.streak.first() shouldBeEqualTo 0
            sut.recordCorrect("uid:2") shouldBeEqualTo 1

            // The first account's own streak is intact when it comes back.
            session.value = IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1")
            sut.currentStreak shouldBeEqualTo 2
        }

    @Test
    fun `a wrong answer only resets its own accounts streak`() =
        runTest {
            sut.recordCorrect("uid:1")
            sut.recordCorrect("uid:2")

            sut.recordWrong("uid:2")

            sut.currentStreak shouldBeEqualTo 1
            session.value = IqSession(SessionStatus.AUTHENTICATED, "0", "uid:2")
            sut.currentStreak shouldBeEqualTo 0
        }

    @Test
    fun `a streak left by the pre-namespace version is not inherited by any account`() {
        preferences.edit().putInt("answer_feedback_streak", 9).apply()

        val migrated = AnswerFeedbackPreferencesImpl(preferences, sessionManager)

        migrated.currentStreak shouldBeEqualTo 0
        preferences.getInt("answer_feedback_streak", -1) shouldBeEqualTo -1
    }

    @Test
    fun `animations and haptics stay device-wide settings`() =
        runTest {
            sut.setAnimationsEnabled(false)
            sut.setHapticsEnabled(false)

            session.value = IqSession(SessionStatus.AUTHENTICATED, "0", "uid:2")

            sut.currentAnimationsEnabled shouldBeEqualTo false
            sut.currentHapticsEnabled shouldBeEqualTo false
        }

    @Test
    fun `a guest session has no streak to write`() {
        sut.recordCorrect(null) shouldBeEqualTo 0
        preferences.all.keys.none { it.startsWith("answer_feedback_streak") } shouldBeEqualTo true
    }
}
