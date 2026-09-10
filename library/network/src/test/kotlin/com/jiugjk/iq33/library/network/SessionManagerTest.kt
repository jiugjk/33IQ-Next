package com.jiugjk.iq33.library.network

import io.mockk.coEvery
import io.mockk.mockk
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
    fun `guest probe reply is reported as guest, not as a session`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"guest"}"""

            val session = sut.refreshFromServer()

            session.status shouldBeEqualTo SessionStatus.GUEST
            session.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an empty JSON object proves nothing and must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns "{}"

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an error envelope must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"error"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a non-JSON body must not count as logged in`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns "<html>error page</html>"

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a real payload is reported as authenticated`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `a failed probe leaves the last verified state untouched`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            coEvery { htmlClient.getText(any()) } throws IOException("offline")

            // Not GUEST (that would be a claim the server never made) and not a fresh AUTHENTICATED.
            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `a login whose verification cannot complete is a failure, not a success`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any()) } throws IOException("probe timed out")

            val result = sut.login("account", "password")

            result shouldBeEqualTo LoginResult.Failure(LoginError.NotVerified)
        }

    @Test
    fun `a failed probe after a previous session does not count the new login as success`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any()) } throws IOException("probe timed out")

            sut.login("account", "password") shouldBeEqualTo LoginResult.Failure(LoginError.NotVerified)
        }

    @Test
    fun `a primitive-only JSON array is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """[null]"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a JSON array of objects is the task list, not a guest`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """[{"id":"1"}]"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `an object with payload besides status is authenticated even without whitelist fields`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","data":[]}"""

            sut.refreshFromServer().status shouldBeEqualTo SessionStatus.AUTHENTICATED
        }

    @Test
    fun `login succeeds when the probe is a data payload without whitelist fields`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"1","uid":"7"}"""
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","data":[]}"""

            sut.login("account", "password") shouldBeEqualTo LoginResult.Success
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo true
        }

    @Test
    fun `an object with only a message is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"message":"maintenance"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `an HTTP error envelope is not evidence of a session`() =
        runTest {
            coEvery { htmlClient.getText(any()) } returns """{"code":500,"message":"temporarily unavailable"}"""

            sut.refreshFromServer().isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a rejected password is reported as a wrong password, not a session`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"passworderror"}"""
            coEvery { htmlClient.getText(any()) } returns """{"status":"guest"}"""

            val result = sut.login("account", "wrong")

            result shouldBeEqualTo LoginResult.Failure(LoginError.WrongPassword)
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `logout removes only the session keys, keeping settings stored alongside them`() =
        runTest {
            preferences.edit().putString("theme_mode", "DARK").apply()
            coEvery { htmlClient.getText(any()) } returns """{"status":"success","tasks":[{"id":"1"}]}"""
            sut.refreshFromServer()

            sut.logout()

            preferences.getString("theme_mode", null) shouldBeEqualTo "DARK"
            sut.sessionFlow.value.isLoggedIn shouldBeEqualTo false
        }

    @Test
    fun `a refresh that was in flight during logout cannot restore the session`() =
        runTest {
            coEvery { htmlClient.getText(any()) } coAnswers {
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
}
