package com.jiugjk.iq33.library.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val preferences = FakeSharedPreferences()
    private val cookieJar = PersistentCookieJar(preferences)
    private val htmlClient = mockk<IqHtmlClient>()
    private val sut = SessionManager(preferences, cookieJar, htmlClient)

    @Test
    fun `verified login persists a stable UID namespace across logout and login`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","data":[]}"""
            sut.login("account", "password")
            sut.sessionFlow.value.accountKey shouldBeEqualTo "uid:7"
            SessionManager(preferences, cookieJar, htmlClient).sessionFlow.value.accountKey shouldBeEqualTo "uid:7"
            sut.logout()
            sut.sessionFlow.value.accountKey shouldBeEqualTo null
            sut.login("account", "password")
            sut.sessionFlow.value.accountKey shouldBeEqualTo "uid:7"
        }

    @Test
    fun `a failed account switch does not keep the previous accounts namespace`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","data":[]}"""
            sut.login("account", "password")
            coEvery { htmlClient.postFormForText(any(), any()) } throws IOException("offline")
            sut.login("other", "password")
            sut.sessionFlow.value.accountKey shouldBeEqualTo null
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `legacy authenticated sessions receive a persistent opaque namespace`() {
        preferences.edit().putString("session_status", "AUTHENTICATED").apply()
        val first = SessionManager(preferences, cookieJar, htmlClient).sessionFlow.value.accountKey
        (first?.startsWith("local:") == true) shouldBeEqualTo true
        SessionManager(preferences, cookieJar, htmlClient).sessionFlow.value.accountKey shouldBeEqualTo first
    }

    @Test
    fun `guest probe reply is reported as guest, not as a session`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"guest"}"""

            val session = sut.refreshFromServer()

            session.status shouldBeEqualTo SessionStatus.GUEST
            session.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an empty JSON object proves nothing and must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns "{}"

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an error envelope must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"error"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a non-JSON body must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns "<html>error page</html>"

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a real payload is reported as authenticated`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `a failed probe leaves the last verified state untouched`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            coEvery { htmlClient.getText(any(), any()) } throws IOException("offline")

            // Not GUEST (that would be a claim the server never made) and not a fresh AUTHENTICATED.
            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `a login whose verification cannot complete is a failure, not a success`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any(), any()) } throws IOException("probe timed out")

            val result = sut.login("account", "password")

            result shouldBeEqualTo LoginResult.Failure(LoginError.NotVerified)
        }

    @Test
    fun `a failed probe after a previous session does not count the new login as success`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any(), any()) } throws IOException("probe timed out")

            sut.login("account", "password") shouldBeEqualTo LoginResult.Failure(LoginError.NotVerified)
        }

    @Test
    fun `a primitive-only JSON array is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """[null]"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a JSON array of objects is the task list, not a guest`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """[{"id":"1"}]"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `an object with payload besides status is authenticated even without whitelist fields`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","data":[]}"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `login succeeds when the probe is a data payload without whitelist fields`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","data":[]}"""

            sut.login("account", "password") shouldBeEqualTo LoginResult.Success
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo true
        }

    @Test
    fun `an object with only a message is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"message":"maintenance"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an HTTP error envelope is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns """{"code":500,"message":"temporarily unavailable"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a rejected password is reported as a wrong password, not a session`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"passworderror"}"""
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"guest"}"""

            val result = sut.login("account", "wrong")

            result shouldBeEqualTo LoginResult.Failure(LoginError.WrongPassword)
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `logout removes only the session keys, keeping settings stored alongside them`() =
        runTest {
            preferences.edit().putString("theme_mode", "DARK").apply()
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            sut.logout()

            preferences.getString("theme_mode", null) shouldBeEqualTo "DARK"
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a refresh that was in flight during logout cannot restore the session`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } coAnswers {
                // Log out while the probe is "in flight", i.e. before its reply is applied. The reply
                // is then returned anyway, so this exercises the generation guard rather than just
                // the cancellation that normally aborts such a probe.
                sut.logout()
                """{"status":"success","tasks":[{"id":"1"}]}"""
            }

            // In its own coroutine: logging out cancels the coroutine that started the refresh.
            launch { sut.refreshFromServer() }.join()

            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a JSON null score is skipped so a valid field still decides`() =
        runTest {
            // JsonNull's own `content` is the string "null"; accepting it would both display "null"
            // and stop the search before the field that really carries the number.
            login()
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":null,"myScore":100}"""

            sut.refreshFromServer().score shouldBeEqualTo "100"
        }

    @Test
    fun `empty and non-numeric scores are skipped, and zero is a real score`() =
        runTest {
            login()
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":"","myscore":"0"}"""
            sut.refreshFromServer().score shouldBeEqualTo "0"

            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":"n/a","userinfo":{"score":"77"}}"""
            sut.refreshFromServer().score shouldBeEqualTo "77"

            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":42}"""
            sut.refreshFromServer().score shouldBeEqualTo "42"
        }

    @Test
    fun `a reply whose only score field is invalid keeps the cached value instead of overwriting it`() =
        runTest {
            login()
            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":"123"}"""
            sut.refreshFromServer().score shouldBeEqualTo "123"

            coEvery { htmlClient.getText(any(), any()) } returns """{"status":"success","score":null}"""

            sut.refreshFromServer().score shouldBeEqualTo "123"
        }

    @Test
    fun `a score captured under another account or an older session is refused`() =
        runTest {
            login()
            val epoch = sut.sessionEpoch
            sut.applyServerScore("500", accountKey = "uid:7", epoch = epoch) shouldBeEqualTo true
            sut.sessionFlow.value.score shouldBeEqualTo "500"

            // Same session, different account: a reply for someone else.
            sut.applyServerScore("1", accountKey = "uid:8", epoch = epoch) shouldBeEqualTo false
            sut.applyOptimisticDelta(5, accountKey = "uid:8", epoch = epoch) shouldBeEqualTo false

            // Same account key, but the session generation moved on (logout / login in between).
            sut.applyServerScore("1", accountKey = "uid:7", epoch = epoch - 1) shouldBeEqualTo false
            sut.applyOptimisticDelta(5, accountKey = "uid:7", epoch = epoch - 1) shouldBeEqualTo false

            sut.sessionFlow.value.score shouldBeEqualTo "500"

            sut.applyOptimisticDelta(5, accountKey = "uid:7", epoch = epoch) shouldBeEqualTo true
            sut.sessionFlow.value.score shouldBeEqualTo "505"
        }

    @Test
    fun `a logged out session accepts no score at all`() =
        runTest {
            login()
            val epoch = sut.sessionEpoch
            sut.logout()

            sut.applyServerScore("500", accountKey = "uid:7", epoch = epoch) shouldBeEqualTo false
            sut.sessionFlow.value.score shouldBeEqualTo null
        }

    @Test
    fun `a lapsed session comes back from the remember-me cookie instead of logging the user out`() =
        runTest {
            seedRememberMe()
            // The session cookie is gone, so the probe sees a guest - until the renewal request has
            // carried the remember-me cookie and 33IQ has handed a session back.
            coEvery { htmlClient.getText(any(), any()) } returnsMany listOf(GUEST_REPLY, TASKS_REPLY)
            coEvery { htmlClient.get(any(), any()) } returns Jsoup.parse("<html><head><title>33IQ</title></head></html>")

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED

            coVerify(exactly = 1) { htmlClient.get(IqConstants.SESSION_RENEWAL_URL, false) }
        }

    @Test
    fun `a lapsed session with no remember-me cookie is reported as the logout it is`() =
        runTest {
            coEvery { htmlClient.getText(any(), any()) } returns GUEST_REPLY

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.GUEST

            // Nothing to renew from, so nothing is sent: this user really does have to log in again.
            coVerify(exactly = 0) { htmlClient.get(any(), any()) }
        }

    @Test
    fun `a remember-me token 33IQ refuses is not retried by every later refresh`() =
        runTest {
            seedRememberMe()
            coEvery { htmlClient.getText(any(), any()) } returns GUEST_REPLY
            coEvery { htmlClient.get(any(), any()) } returns Jsoup.parse("<html><head><title>33IQ</title></head></html>")

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.GUEST
            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.GUEST

            coVerify(exactly = 1) { htmlClient.get(any(), any()) }
        }

    @Test
    fun `a live session re-issues a remember-me cookie that is about to lapse`() =
        runTest {
            seedRememberMe(expiresAt = System.currentTimeMillis() + ONE_DAY_MS)
            coEvery { htmlClient.getText(any(), any()) } returns TASKS_REPLY
            coEvery { htmlClient.get(any(), any()) } returns Jsoup.parse("<html><head><title>33IQ</title></head></html>")

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED

            // Renewed while it still works, which is the whole point: a token that never lapses
            // never leaves the user staring at a login screen.
            coVerify(exactly = 1) { htmlClient.get(IqConstants.SESSION_RENEWAL_URL, false) }
        }

    @Test
    fun `a live session well inside its remember-me window renews nothing`() =
        runTest {
            seedRememberMe()
            coEvery { htmlClient.getText(any(), any()) } returns TASKS_REPLY

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED

            coVerify(exactly = 0) { htmlClient.get(any(), any()) }
        }

    @Test
    fun `logging out drops the remember-me cookie a renewal would have used`() =
        runTest {
            seedRememberMe()
            coEvery { htmlClient.getText(any(), any()) } returns GUEST_REPLY
            coEvery { htmlClient.get(any(), any()) } returns Jsoup.parse("<html><head><title>33IQ</title></head></html>")

            sut.logout()
            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.GUEST

            // An explicit logout is not a lapsed session: nothing may quietly log this user back in.
            coVerify(exactly = 0) { htmlClient.get(any(), any()) }
        }

    /**
     * 33IQ's remember-me cookie was never named by any capture, so the jar recognises it by shape -
     * a cookie with an expiry, which is what outlives the session cookie. These tests set one the
     * same way a Set-Cookie would.
     */
    private fun seedRememberMe(expiresAt: Long = System.currentTimeMillis() + ONE_YEAR_MS) {
        cookieJar.saveFromResponse(
            "https://www.33iq.com/".toHttpUrl(),
            listOf(
                Cookie
                    .Builder()
                    .name("remember")
                    .value("token")
                    .domain("www.33iq.com")
                    .path("/")
                    .expiresAt(expiresAt)
                    .build(),
            ),
        )
    }

    private suspend fun login() {
        coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
        coEvery { htmlClient.getText(any(), any()) } returns TASKS_REPLY
        sut.login("account", "password")
    }

    private companion object {
        const val GUEST_REPLY = """{"status":"guest"}"""
        const val TASKS_REPLY = """{"status":"success","tasks":[{"id":"1"}]}"""
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }
}
