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
