package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * How confident this app is about the current 33IQ session.
 *
 * Only [AUTHENTICATED] means "logged in": everything else - an unreachable server, an error page, a
 * body that is not the probe's own payload - is either an explicit guest reply or simply unknown.
 * Collapsing "unknown" into "logged in" is what used to make a rejected password look like a
 * successful login.
 */
enum class SessionStatus {
    /** 33IQ's own probe endpoint returned this account's real payload. */
    AUTHENTICATED,

    /** 33IQ's own probe endpoint explicitly replied with its guest marker. */
    GUEST,

    /** The probe could not be completed or its reply proved nothing - the session state is unknown. */
    UNKNOWN,
}

data class IqSession(
    val status: SessionStatus = SessionStatus.UNKNOWN,
    /**
     * 学识 score shown by 33IQ next to a logged-in user's name. Always null for now: a real
     * logged-in response was never available to confirm which field carries it.
     */
    val score: String? = null,
    /** Stable local-data namespace. A confirmed login UID is preferred; legacy sessions use an opaque ID. */
    val accountKey: String? = null,
) {
    /** Only a positively verified session counts as logged in - see [SessionStatus]. */
    val isLoggedIn: Boolean get() = status == SessionStatus.AUTHENTICATED
}

sealed interface LoginResult {
    data object Success : LoginResult

    data class Failure(
        val error: LoginError,
    ) : LoginResult
}

/**
 * Why a login attempt did not produce a verified session. Deliberately typed rather than a
 * ready-made sentence: the wording (and its language) belongs to the presentation layer's
 * `strings.xml`, and tests can assert on a category instead of on a message.
 */
sealed interface LoginError {
    /** The login request itself never completed (offline, DNS, TLS, HTTP error). */
    data object NetworkUnavailable : LoginError

    data object UnknownAccount : LoginError

    data object WrongPassword : LoginError

    data object AccountLocked : LoginError

    /**
     * The login POST completed, but the follow-up verification probe could not confirm a session -
     * so this is explicitly *not* reported as a success.
     */
    data object NotVerified : LoginError

    /** 33IQ replied with a status this client does not recognise; [serverStatus] is kept for diagnostics. */
    data class Unknown(
        val serverStatus: String?,
    ) : LoginError
}

/**
 * Tracks whether the app currently holds a logged-in 33IQ session.
 *
 * Login state is verified by calling [IqConstants.GUEST_PROBE_URL] - a real captured app session
 * confirmed this replies `{"status":"guest"}` for an unauthenticated request. The reply is parsed
 * and classified into the three [SessionStatus] cases; an HTTP 200 alone, or the mere presence of
 * cookies, is never treated as proof of a session.
 *
 * Every state commit is guarded by a session generation. [login] and [logout] bump it, so a refresh
 * that was already in flight when the user logged out cannot install its now-stale result - and its
 * HTTP call is cancelled, so its response cannot re-install authentication cookies either.
 */
class SessionManager(
    private val preferences: SharedPreferences,
    private val cookieJar: PersistentCookieJar,
    private val htmlClient: IqHtmlClient,
) {
    private val _sessionFlow = MutableStateFlow(loadPersistedSession())
    val sessionFlow: StateFlow<IqSession> = _sessionFlow.asStateFlow()

    private val generation = AtomicInteger(0)
    private val lock = Any()

    // Jobs of the currently running refreshFromServer calls. Guarded by lock.
    private val inFlightRefreshes = mutableSetOf<Job>()

    /** Logs in using 33IQ's AJAX login endpoint (account can be phone / email / nickname). */
    suspend fun login(
        account: String,
        password: String,
    ): LoginResult {
        // A login attempt supersedes anything already in flight, and any state it might commit.
        invalidateInFlightWork()
        cookieJar.invalidate()
        // Never let an old account's cookies or local progress authenticate a new login attempt.
        _sessionFlow.value = IqSession()
        persist(_sessionFlow.value)
        val startGeneration = generation.get()
        val job = currentCoroutineContext()[Job]

        if (job != null) {
            synchronized(lock) { inFlightRefreshes += job }
        }

        try {
            val rawResponse =
                runCatching {
                    htmlClient.postFormForText(
                        IqConstants.LOGIN_URL,
                        // Field names confirmed from a real captured login request, including the
                        // "ememberme" (not "rememberme") field name as sent by the real app.
                        mapOf("email" to account, "password" to password, "ememberme" to "1"),
                    )
                }.getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable

                    Timber.tag(LOG_TAG).w(throwable, "Login request failed")
                    return LoginResult.Failure(LoginError.NetworkUnavailable)
                }

            val loginPayload = runCatching { Json.parseToJsonElement(rawResponse) as? JsonObject }.getOrNull()
            val status = Classifier.scalarContent(loginPayload)
            val uid =
                (loginPayload?.get("uid") as? JsonPrimitive)?.contentOrNull?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }

            // A real successful login is confirmed to reply {"status":"1","uid":"<id>"}, but the failure
            // status strings still aren't (no failed login was ever captured). Success is therefore
            // decided by the verification probe, which is the only signal confirmed against the real
            // server - the reported status only distinguishes *why* an unverified attempt failed.
            val probed = probeSessionStatus()
            commit(startGeneration, probed, accountKey = uid?.takeIf { status == "1" }?.let { "uid:$it" })

            // Login success is decided by *this* probe, not by whatever commit() kept on screen when
            // the probe was UNKNOWN (that path preserves the last verified session for display).
            return when (probed) {
                SessionStatus.AUTHENTICATED -> LoginResult.Success
                SessionStatus.GUEST -> LoginResult.Failure(loginErrorFor(status))
                SessionStatus.UNKNOWN -> LoginResult.Failure(LoginError.NotVerified)
            }
        } finally {
            if (job != null) {
                synchronized(lock) { inFlightRefreshes -= job }
            }
        }
    }

    /**
     * Re-verifies the session against 33IQ. A result that cannot be verified leaves the last
     * verified state untouched rather than downgrading (or upgrading) it on a guess.
     */
    suspend fun refreshFromServer(): IqSession {
        val startGeneration = generation.get()
        val job = currentCoroutineContext()[Job]

        if (job != null) {
            synchronized(lock) { inFlightRefreshes += job }
        }

        val probed =
            try {
                probeSessionStatus()
            } finally {
                if (job != null) {
                    synchronized(lock) { inFlightRefreshes -= job }
                }
            }

        return commit(startGeneration, probed)
    }

    fun logout() {
        invalidateInFlightWork()

        cookieJar.invalidate()
        // Only the session's own keys - this SharedPreferences file is shared with the theme
        // setting, which a logout must not wipe.
        preferences.edit {
            remove(PREF_KEY_STATUS)
            remove(PREF_KEY_SCORE)
            remove(PREF_KEY_ACCOUNT)
            remove(PREF_KEY_LEGACY_LOGGED_IN)
        }
        _sessionFlow.value = IqSession(status = SessionStatus.GUEST)
    }

    private suspend fun probeSessionStatus(): SessionStatus =
        runCatching {
            val url = "${IqConstants.GUEST_PROBE_URL}?lang=zh-cn&p=3&time=${System.currentTimeMillis()}"

            Classifier.classify(htmlClient.getText(url))
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable

            Timber.tag(LOG_TAG).w(throwable, "Failed to refresh session")
            SessionStatus.UNKNOWN
        }

    /** Applies [probed] only if no login/logout happened while the probe was running. */
    private fun commit(
        startGeneration: Int,
        probed: SessionStatus,
        accountKey: String? = null,
    ): IqSession =
        synchronized(lock) {
            val current = _sessionFlow.value

            if (generation.get() != startGeneration) return current

            // An unverifiable probe must not overwrite what was last verified - in particular it
            // must never turn a rejected login into a session.
            if (probed == SessionStatus.UNKNOWN) return current

            val session =
                current.copy(
                    status = probed,
                    score = if (probed == SessionStatus.GUEST) null else current.score,
                    accountKey = if (probed == SessionStatus.GUEST) null else accountKey ?: current.accountKey ?: newLocalAccountKey(),
                )

            _sessionFlow.value = session
            persist(session)

            session
        }

    private fun invalidateInFlightWork() {
        generation.incrementAndGet()

        val jobs = synchronized(lock) { inFlightRefreshes.toList().also { inFlightRefreshes.clear() } }

        // Cancelling the coroutine aborts its HTTP call (see IqHtmlClient), so a stale response
        // cannot re-install cookies for the session that was just discarded.
        jobs.forEach { it.cancel() }
    }

    private fun loginErrorFor(status: String?): LoginError =
        when (status) {
            "usernameerror" -> LoginError.UnknownAccount
            "passworderror" -> LoginError.WrongPassword
            "locked", "login-locked" -> LoginError.AccountLocked
            else -> LoginError.Unknown(status)
        }

    private fun loadPersistedSession(): IqSession {
        val persisted = preferences.getString(PREF_KEY_STATUS, null)
        val status =
            when {
                persisted != null -> runCatching { SessionStatus.valueOf(persisted) }.getOrDefault(SessionStatus.UNKNOWN)
                // Installs that predate SessionStatus only stored a boolean.
                preferences.getBoolean(PREF_KEY_LEGACY_LOGGED_IN, false) -> SessionStatus.AUTHENTICATED
                else -> SessionStatus.UNKNOWN
            }

        val accountKey =
            if (status == SessionStatus.AUTHENTICATED) {
                preferences.getString(PREF_KEY_ACCOUNT, null) ?: newLocalAccountKey().also { key ->
                    preferences.edit { putString(PREF_KEY_ACCOUNT, key) }
                }
            } else {
                null
            }
        return IqSession(status = status, score = preferences.getString(PREF_KEY_SCORE, null), accountKey = accountKey)
    }

    private fun newLocalAccountKey(): String = "local:${UUID.randomUUID()}"

    private fun persist(session: IqSession) {
        preferences.edit {
            putString(PREF_KEY_STATUS, session.status.name)
            putString(PREF_KEY_SCORE, session.score)
            putString(PREF_KEY_ACCOUNT, session.accountKey)
            remove(PREF_KEY_LEGACY_LOGGED_IN)
        }
    }

    /**
     * Classifies a probe reply.
     *
     * The only shape confirmed live is the guest marker `{"status":"guest"}`. A logged-in `/app/taskall`
     * body has never been captured, but a working login before the evidence-field whitelist showed it is
     * either a JSON array (the task list) or an object with payload besides `status`. Restricting success
     * to a handful of guessed field names turned those real replies into unknown: cookies were installed
     * (answering still worked) while the UI stayed logged out.
     *
     * Error envelopes and primitive-only arrays stay unknown - those were review counterexamples, not
     * 33IQ's guest marker.
     */
    private object Classifier {
        fun classify(body: String): SessionStatus {
            val payload = runCatching { Json.parseToJsonElement(body) }.getOrNull()

            return when (payload) {
                is JsonArray -> classifyArray(payload)
                is JsonObject -> classifyObject(payload)
                else -> SessionStatus.UNKNOWN
            }
        }

        fun scalarContent(obj: JsonObject?): String? {
            val primitive = obj?.get(STATUS_FIELD) as? JsonPrimitive ?: return null

            return primitive.contentOrNull ?: primitive.content.takeIf { it.isNotEmpty() }
        }

        private fun classifyArray(payload: JsonArray): SessionStatus =
            if (payload.isEmpty() || payload.any { it is JsonObject }) {
                SessionStatus.AUTHENTICATED
            } else {
                SessionStatus.UNKNOWN
            }

        private fun classifyObject(obj: JsonObject): SessionStatus {
            val status = scalarContent(obj)

            return when {
                status == GUEST_STATUS -> SessionStatus.GUEST
                status != null && status.lowercase() in ERROR_STATUSES -> SessionStatus.UNKNOWN
                AUTH_EVIDENCE_FIELDS.any { field -> field in obj } -> SessionStatus.AUTHENTICATED
                obj.keys.isNotEmpty() && obj.keys.all { it in NON_ACCOUNT_ENVELOPE_KEYS } -> SessionStatus.UNKNOWN
                obj.keys.any { it != STATUS_FIELD } -> SessionStatus.AUTHENTICATED
                else -> SessionStatus.UNKNOWN
            }
        }
    }

    private companion object {
        const val LOG_TAG = "Network"
        const val STATUS_FIELD = "status"
        const val GUEST_STATUS = "guest"
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1")
        val AUTH_EVIDENCE_FIELDS = setOf("uid", "username", "email", "tasks", "userinfo", "score")
        val NON_ACCOUNT_ENVELOPE_KEYS = setOf("message", "msg", "code", "error", "errno")
        const val PREF_KEY_STATUS = "session_status"
        const val PREF_KEY_SCORE = "score"
        const val PREF_KEY_ACCOUNT = "session_account_key"
        const val PREF_KEY_LEGACY_LOGGED_IN = "is_logged_in"
    }
}
