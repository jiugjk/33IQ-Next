package com.jiugjk.iq33.library.network

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

/**
 * 学识 log isolation between accounts on one device.
 *
 * The paths that matter are the ones that never press "log out": an expired session dropping to
 * guest, a second account logging in, and a cold start of the app.
 */
class KnowledgeChangeLogTest {
    private val preferences = FakeSharedPreferences()
    private val session = MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1"))
    private val sessionManager = mockk<SessionManager> { every { sessionFlow } returns session }
    private val sut = KnowledgeChangeLog(preferences, sessionManager)

    @Test
    fun `entries belong to the account that earned them`() =
        runTest {
            sut.append(accountKey = "uid:1", questionId = 1, delta = 3)

            sut.recent.first().map { it.questionId } shouldBeEqualTo listOf(1L)

            // Session expires: guest sees nothing, without anyone pressing log out.
            session.value = IqSession(SessionStatus.GUEST)
            sut.recent.first() shouldBeEqualTo emptyList()

            // A second account logs in on the same device.
            session.value = IqSession(SessionStatus.AUTHENTICATED, "0", "uid:2")
            sut.recent.first() shouldBeEqualTo emptyList()

            sut.append(accountKey = "uid:2", questionId = 2, delta = -1)
            sut.recent.first().map { it.questionId } shouldBeEqualTo listOf(2L)

            // Back to the first account: its own history is still there and still only its own.
            session.value = IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1")
            sut.recent.first().map { it.questionId } shouldBeEqualTo listOf(1L)
        }

    @Test
    fun `a restart reads the same per-account log`() =
        runTest {
            sut.append(accountKey = "uid:1", questionId = 1, delta = 3)

            val restarted = KnowledgeChangeLog(preferences, sessionManager)

            restarted.current("uid:1").map { it.questionId } shouldBeEqualTo listOf(1L)
            restarted.current("uid:2") shouldBeEqualTo emptyList()
        }

    @Test
    fun `clearing one account leaves the other untouched`() =
        runTest {
            sut.append(accountKey = "uid:1", questionId = 1, delta = 3)
            sut.append(accountKey = "uid:2", questionId = 2, delta = 4)

            sut.clear("uid:1")

            sut.current("uid:1") shouldBeEqualTo emptyList()
            sut.current("uid:2").map { it.questionId } shouldBeEqualTo listOf(2L)
        }

    @Test
    fun `a log left by the pre-namespace version is dropped rather than shown to whoever logs in`() {
        preferences
            .edit()
            .putString("knowledge_delta_log", """[{"questionId":9,"title":"","delta":5,"at":1}]""")
            .apply()

        val migrated = KnowledgeChangeLog(preferences, sessionManager)

        migrated.current("uid:1") shouldBeEqualTo emptyList()
        preferences.getString("knowledge_delta_log", null) shouldBeEqualTo null
    }

    @Test
    fun `a guest session has nowhere to write and nothing to show`() =
        runTest {
            session.value = IqSession(SessionStatus.GUEST)

            sut.append(accountKey = null, questionId = 1, delta = 3)

            sut.recent.first() shouldBeEqualTo emptyList()
            preferences.all.keys.none { it.startsWith("knowledge_delta_log") } shouldBeEqualTo true
        }
}
