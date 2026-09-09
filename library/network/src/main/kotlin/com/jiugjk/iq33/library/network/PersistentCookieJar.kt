package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/*
 * A [CookieJar] that persists cookies (session id, remember-me token, ...) to [SharedPreferences] so
 * a logged-in session survives process death.
 *
 * Cookies are stored under their RFC 6265 identity - name + domain + path - rather than name alone,
 * so two cookies that differ only by path can coexist instead of overwriting each other. Reads go
 * through OkHttp's own [Cookie.matches], which applies the domain, path and Secure rules against the
 * request URL; the jar never sends a cookie to a URL it was not scoped to, even if a redirect leaves
 * the host this app normally talks to.
 *
 * Every read, mutation and persist runs under [lock]. OkHttp calls a jar from whichever IO thread is
 * running a call, so overlapping requests would otherwise iterate the map while another thread
 * mutates it, and interleave a "mutate, then persist" pair into a stale snapshot.
 */
class PersistentCookieJar(
    private val preferences: SharedPreferences,
) : CookieJar {
    private val lock = Any()

    // Guarded by lock.
    private val cookies = mutableMapOf<CookieKey, Cookie>()

    init {
        synchronized(lock) { restore() }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        if (cookies.isEmpty()) return

        synchronized(lock) {
            cookies.forEach { cookie ->
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    this.cookies.remove(cookie.key())
                } else {
                    this.cookies[cookie.key()] = cookie
                }
            }

            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()

        return synchronized(lock) {
            val expiredKeys = cookies.filterValues { cookie -> cookie.expiresAt <= now }.keys.toList()

            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { cookies.remove(it) }
                persist()
            }

            cookies.values.filter { cookie -> cookie.matches(url) }
        }
    }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            preferences.edit { remove(PREF_KEY_COOKIES) }
        }
    }

    // Must be called while holding lock.
    private fun persist() {
        val serialized = cookies.values.joinToString(separator = COOKIE_SEPARATOR) { it.toString() }

        preferences.edit { putString(PREF_KEY_COOKIES, serialized) }
    }

    // Must be called while holding lock.
    private fun restore() {
        val serialized = preferences.getString(PREF_KEY_COOKIES, null) ?: return
        val url =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(HOST)
                .build()
        val now = System.currentTimeMillis()

        serialized
            .split(COOKIE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { cookieString -> runCatching { Cookie.parse(url, cookieString) }.getOrNull() }
            // A cookie that expired while the app was closed is not a session - dropping it here
            // keeps an expired jar from looking like a live one.
            .filter { cookie -> cookie.expiresAt > now }
            .forEach { cookie -> cookies[cookie.key()] = cookie }

        persist()
    }

    private fun Cookie.key() = CookieKey(name = name, domain = domain, path = path)

    private data class CookieKey(
        val name: String,
        val domain: String,
        val path: String,
    )

    private companion object {
        const val HOST = "www.33iq.com"
        const val PREF_KEY_COOKIES = "persisted_cookies"
        const val COOKIE_SEPARATOR = "\n"
    }
}
