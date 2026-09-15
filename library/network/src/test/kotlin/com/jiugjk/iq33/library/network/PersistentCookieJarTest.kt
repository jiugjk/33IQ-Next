package com.jiugjk.iq33.library.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class PersistentCookieJarTest {
    private val preferences = FakeSharedPreferences()
    private val sut = PersistentCookieJar(preferences)

    @Test
    fun `cookies that differ only by path are both kept and matched per request`() {
        sut.saveFromResponse(
            REQUEST_URL,
            listOf(
                cookie(name = "sid", value = "index-session", path = "/index"),
                cookie(name = "sid", value = "app-session", path = "/app"),
            ),
        )

        sut.loadForRequest("https://www.33iq.com/index/login".toHttpUrl()).map { it.value } shouldBeEqualTo listOf("index-session")
        sut.loadForRequest("https://www.33iq.com/app/taskall".toHttpUrl()).map { it.value } shouldBeEqualTo listOf("app-session")
    }

    @Test
    fun `a cookie is not sent to a host it was not scoped to`() {
        sut.saveFromResponse(REQUEST_URL, listOf(cookie(name = "sid", value = "session")))

        sut.loadForRequest("https://example.com/".toHttpUrl()).shouldBeEmpty()
    }

    @Test
    fun `an expired cookie is dropped rather than sent`() {
        sut.saveFromResponse(
            REQUEST_URL,
            listOf(cookie(name = "sid", value = "session"), cookie(name = "old", value = "gone", expiresAt = 1L)),
        )

        sut.loadForRequest(REQUEST_URL).map { it.name } shouldBeEqualTo listOf("sid")
    }

    @Test
    fun `cookies survive a restart, and expired ones do not come back`() {
        sut.saveFromResponse(
            REQUEST_URL,
            listOf(cookie(name = "sid", value = "session"), cookie(name = "stale", value = "gone", expiresAt = PAST_EXPIRY)),
        )

        val restored = PersistentCookieJar(preferences)

        restored.loadForRequest(REQUEST_URL).map { it.name } shouldBeEqualTo listOf("sid")
    }

    @Test
    fun `clear removes the persisted cookies too`() {
        sut.saveFromResponse(REQUEST_URL, listOf(cookie(name = "sid", value = "session")))

        sut.clear()

        PersistentCookieJar(preferences).loadForRequest(REQUEST_URL).shouldBeEmpty()
    }

    @Test
    fun `a session cookie is not mistaken for the credential a renewal needs`() {
        // No Expires/Max-Age: this is the half that lapses, and the reason the app gets logged out.
        sut.saveFromResponse(REQUEST_URL, listOf(sessionCookie(name = "sid", value = "session")))

        sut.hasCredential() shouldBeEqualTo false
        sut.credentialExpiry() shouldBeEqualTo null
    }

    @Test
    fun `the remember-me cookie is what a renewal is decided from, whatever it is called`() {
        sut.saveFromResponse(
            REQUEST_URL,
            listOf(sessionCookie(name = "sid", value = "session"), cookie(name = "whatever-33iq-calls-it", value = "token")),
        )

        sut.hasCredential() shouldBeEqualTo true
        sut.credentialExpiry() shouldBeEqualTo FAR_FUTURE
    }

    @Test
    fun `an expired remember-me cookie is nothing left to renew from`() {
        sut.saveFromResponse(REQUEST_URL, listOf(cookie(name = "remember", value = "token", expiresAt = PAST_EXPIRY)))

        sut.hasCredential() shouldBeEqualTo false
    }

    @Test
    fun `the credential survives a restart, which is what keeps a reopened app logged in`() {
        sut.saveFromResponse(REQUEST_URL, listOf(cookie(name = "remember", value = "token")))

        PersistentCookieJar(preferences).hasCredential() shouldBeEqualTo true
    }

    private fun sessionCookie(
        name: String,
        value: String,
    ) = Cookie
        .Builder()
        .name(name)
        .value(value)
        .domain("www.33iq.com")
        .path("/")
        .build()

    private fun cookie(
        name: String,
        value: String,
        path: String = "/",
        expiresAt: Long = FAR_FUTURE,
    ) = Cookie
        .Builder()
        .name(name)
        .value(value)
        .domain("www.33iq.com")
        .path(path)
        .expiresAt(expiresAt)
        .build()

    private companion object {
        val REQUEST_URL = "https://www.33iq.com/".toHttpUrl()
        const val FAR_FUTURE = 4_102_444_800_000L

        // In the past, but late enough that OkHttp keeps it as a real (persistent) cookie value.
        const val PAST_EXPIRY = 1_000_000_000_000L
    }
}
