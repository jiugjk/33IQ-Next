package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

data class IqSession(
    val isLoggedIn: Boolean = false,
    /** 学识 score shown by 33IQ next to a logged-in user's name, when it could be parsed. */
    val score: String? = null,
)

sealed interface LoginResult {
    data object Success : LoginResult

    data class Failure(
        val message: String,
    ) : LoginResult
}

/**
 * Tracks whether the app currently holds a logged-in 33IQ session.
 *
 * 33IQ has no API to introspect "am I logged in" directly. Every server-rendered page embeds a small
 * `<script>` block with `var user_type="-1";` for guests, so login state is detected by re-fetching a
 * lightweight page and reading that variable back out after every login/logout action.
 */
class SessionManager(
    private val preferences: SharedPreferences,
    private val cookieJar: PersistentCookieJar,
    private val htmlClient: IqHtmlClient,
) {
    private val _sessionFlow = MutableStateFlow(loadPersistedSession())
    val sessionFlow: StateFlow<IqSession> = _sessionFlow.asStateFlow()

    /** Logs in using 33IQ's AJAX login endpoint (account can be phone / email / nickname). */
    suspend fun login(
        account: String,
        password: String,
    ): LoginResult {
        val rawResponse =
            runCatching {
                htmlClient.postFormForText(
                    IqConstants.LOGIN_URL,
                    mapOf("email" to account, "password" to password, "rememberme" to "1"),
                )
            }.getOrElse { throwable ->
                Timber.tag("Network").w(throwable, "Login request failed")
                return LoginResult.Failure("网络请求失败，请检查网络连接")
            }

        val status = Regex(""""status"\s*:\s*"([^"]*)${'"'}""").find(rawResponse)?.groupValues?.get(1)

        // The exact set of success/error status strings returned by 33IQ's login endpoint isn't fully
        // confirmed (this client has no way to log in with a real, verified account during development).
        // Rather than trust a guessed "success" string, re-check the real, verified `user_type` signal.
        val session = refreshFromServer()

        return if (session.isLoggedIn) {
            LoginResult.Success
        } else {
            LoginResult.Failure(describeLoginError(status))
        }
    }

    /** Applies cookies captured from an in-app WebView after the user completed login there. */
    suspend fun applyWebViewCookies(rawCookieHeader: String): IqSession {
        cookieJar.setCookiesFromRawHeader(rawCookieHeader)

        return refreshFromServer()
    }

    suspend fun refreshFromServer(): IqSession {
        val session =
            runCatching {
                val document = htmlClient.get(IqConstants.BASE_URL)
                val script =
                    document
                        .select("script")
                        .map { it.data() }
                        .firstOrNull { it.contains("user_type") } ?: ""

                val userType =
                    Regex("""var\s+user_type\s*=\s*"(-?\d+)${'"'}""").find(script)?.groupValues?.get(1)
                val score =
                    Regex("""var\s+userScore\s*=\s*"([^"]*)${'"'}""").find(script)?.groupValues?.get(1)

                IqSession(
                    isLoggedIn = userType != null && userType != GUEST_USER_TYPE,
                    score = score?.takeIf { it.isNotBlank() },
                )
            }.getOrElse { throwable ->
                Timber.tag("Network").w(throwable, "Failed to refresh session")
                IqSession(isLoggedIn = cookieJar.hasCookies())
            }

        _sessionFlow.value = session
        persist(session)

        return session
    }

    fun logout() {
        cookieJar.clear()
        preferences.edit { clear() }
        _sessionFlow.value = IqSession()
    }

    private fun describeLoginError(status: String?): String =
        when (status) {
            "usernameerror" -> "账号不存在"
            "passworderror" -> "密码错误"
            "locked", "login-locked" -> "账号已被锁定，请稍后再试"
            null -> "登录失败，请稍后重试"
            else -> "登录失败（$status），请稍后重试"
        }

    private fun loadPersistedSession() =
        IqSession(
            isLoggedIn = preferences.getBoolean(PREF_KEY_LOGGED_IN, false),
            score = preferences.getString(PREF_KEY_SCORE, null),
        )

    private fun persist(session: IqSession) {
        preferences.edit {
            putBoolean(PREF_KEY_LOGGED_IN, session.isLoggedIn)
            putString(PREF_KEY_SCORE, session.score)
        }
    }

    private companion object {
        const val GUEST_USER_TYPE = "-1"
        const val PREF_KEY_LOGGED_IN = "is_logged_in"
        const val PREF_KEY_SCORE = "score"
    }
}
