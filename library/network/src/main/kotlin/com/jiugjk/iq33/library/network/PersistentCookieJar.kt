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
 * This app only ever talks to a single host ([IqConstants.BASE_URL]), so cookies are kept in a flat,
 * host-agnostic map keyed by cookie name rather than a full multi-host cookie store.
 */
class PersistentCookieJar(
    private val preferences: SharedPreferences,
) : CookieJar {
    private val cookiesByName = mutableMapOf<String, Cookie>()

    init {
        restore()
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        if (cookies.isEmpty()) return

        cookies.forEach { cookie ->
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                cookiesByName.remove(cookie.name)
            } else {
                cookiesByName[cookie.name] = cookie
            }
        }

        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookiesByName.values.filter { it.expiresAt > System.currentTimeMillis() }

    fun hasCookies(): Boolean = cookiesByName.isNotEmpty()

    /** Merges cookies parsed from a `document.cookie`-style `"name=value; name2=value2"` string. */
    fun setCookiesFromRawHeader(rawCookieHeader: String) {
        rawCookieHeader
            .split(";")
            .map { pair -> pair.trim().split("=", limit = 2) }
            .filter { parts -> parts.size == 2 && parts[0].isNotBlank() }
            .map { parts ->
                Cookie
                    .Builder()
                    .name(parts[0])
                    .value(parts[1])
                    .domain(HOST)
                    .path("/")
                    // Session cookies captured from the WebView have no explicit expiry;
                    // keep them for a year so the persisted session survives restarts.
                    .expiresAt(System.currentTimeMillis() + ONE_YEAR_MILLIS)
                    .build()
            }.forEach { cookie -> cookiesByName[cookie.name] = cookie }

        persist()
    }

    fun clear() {
        cookiesByName.clear()
        preferences.edit { remove(PREF_KEY_COOKIES) }
    }

    private fun persist() {
        val serialized = cookiesByName.values.joinToString(separator = COOKIE_SEPARATOR) { it.toString() }

        preferences.edit { putString(PREF_KEY_COOKIES, serialized) }
    }

    private fun restore() {
        val serialized = preferences.getString(PREF_KEY_COOKIES, null) ?: return
        val url =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(HOST)
                .build()

        serialized
            .split(COOKIE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { cookieString -> runCatching { Cookie.parse(url, cookieString) }.getOrNull() }
            .forEach { cookie -> cookiesByName[cookie.name] = cookie }
    }

    private companion object {
        const val HOST = "www.33iq.com"
        const val PREF_KEY_COOKIES = "persisted_cookies"
        const val COOKIE_SEPARATOR = "\n"
        const val ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
