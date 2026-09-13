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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
     * 学识 shown next to a logged-in user. Null means "unknown yet" — UI shows a skeleton, never 0.
     * Server values (probe / submit myScore) replace this unconditionally; local deltas only adjust
     * an already-known score.
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
@Suppress("TooManyFunctions")
class SessionManager(
    private val preferences: SharedPreferences,
    private val cookieJar: PersistentCookieJar,
    private val htmlClient: IqHtmlClient,
) {
    private val _sessionFlow = MutableStateFlow(loadPersistedSession())
    val sessionFlow: StateFlow<IqSession> = _sessionFlow.asStateFlow()

    private val generation = AtomicInteger(0)
    private val lock = Any()

    /**
     * The session generation a background request started under.
     *
     * A caller that produces account-scoped side effects (a submitted answer's 学识, a streak, a log
     * entry) reads this *before* the request and hands it back to [applyServerScore] /
     * [applyOptimisticDelta]. A login or logout bumps the generation, so a reply that lands after
     * the account changed can be dropped instead of being applied to whoever is logged in now.
     */
    val sessionEpoch: Int get() = generation.get()

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
            return when (probed.status) {
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

    /**
     * Unconditional server truth. Replaces any optimistic local score.
     * Pass the raw display string (usually digits from myScore / probe).
     *
     * [accountKey] and [epoch] describe the session the value was fetched for; both are re-checked
     * inside the same lock that commits the state, so a late reply for account A can never move
     * account B's 学识. Returns whether the value was applied.
     */
    fun applyServerScore(
        score: String,
        accountKey: String? = null,
        epoch: Int? = null,
    ): Boolean =
        synchronized(lock) {
            val current = _sessionFlow.value
            if (!isStillCurrent(current, accountKey, epoch)) return false
            val session = current.copy(score = score)
            _sessionFlow.value = session
            persist(session)
            true
        }

    fun applyServerScore(
        score: Int,
        accountKey: String? = null,
        epoch: Int? = null,
    ): Boolean = applyServerScore(score.toString(), accountKey, epoch)

    /**
     * Optimistic only: adjusts an already-known score by [delta]. Does nothing when score is unknown
     * (keeps skeleton — never invents 0).
     */
    fun applyOptimisticDelta(
        delta: Int,
        accountKey: String? = null,
        epoch: Int? = null,
    ): Boolean =
        synchronized(lock) {
            val current = _sessionFlow.value
            val base = current.score?.toIntOrNull()

            if (delta == 0 || base == null || !isStillCurrent(current, accountKey, epoch)) {
                false
            } else {
                val session = current.copy(score = (base + delta).toString())
                _sessionFlow.value = session
                persist(session)
                true
            }
        }

    /**
     * Whether a side effect captured under [accountKey] / [epoch] still belongs to the live session.
     *
     * A null [accountKey] or [epoch] means the caller could not name an owner (legacy call sites);
     * those keep the previous "logged in is enough" rule rather than silently widening it.
     */
    private fun isStillCurrent(
        current: IqSession,
        accountKey: String?,
        epoch: Int?,
    ): Boolean {
        if (!current.isLoggedIn) return false
        if (epoch != null && epoch != generation.get()) return false
        return accountKey == null || accountKey == current.accountKey
    }

    private suspend fun probeSessionStatus(): ProbeOutcome =
        runCatching {
            val url = "${IqConstants.GUEST_PROBE_URL}?lang=zh-cn&p=3&time=${System.currentTimeMillis()}"
            Classifier.classifyWithScore(htmlClient.getText(url))
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable

            Timber.tag(LOG_TAG).w(throwable, "Failed to refresh session")
            ProbeOutcome(SessionStatus.UNKNOWN)
        }

    /** Applies [probed] only if no login/logout happened while the probe was running. */
    private fun commit(
        startGeneration: Int,
        probed: ProbeOutcome,
        accountKey: String? = null,
    ): IqSession =
        synchronized(lock) {
            val current = _sessionFlow.value

            if (generation.get() != startGeneration) return current

            // An unverifiable probe must not overwrite what was last verified - in particular it
            // must never turn a rejected login into a session.
            if (probed.status == SessionStatus.UNKNOWN) return current

            val nextScore =
                when {
                    probed.status == SessionStatus.GUEST -> null
                    // Server field available → unconditional override (no merge with local).
                    probed.serverScore != null -> probed.serverScore
                    else -> current.score
                }

            val session =
                current.copy(
                    status = probed.status,
                    score = nextScore,
                    accountKey =
                        if (probed.status ==
                            SessionStatus.GUEST
                        ) {
                            null
                        } else {
                            accountKey ?: current.accountKey ?: newLocalAccountKey()
                        },
                )

            _sessionFlow.value = session
            persist(session)

            session
        }

    private data class ProbeOutcome(
        val status: SessionStatus,
        val serverScore: String? = null,
    )

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
        fun classifyWithScore(body: String): ProbeOutcome {
            val payload = runCatching { Json.parseToJsonElement(body) }.getOrNull()

            return when (payload) {
                is JsonArray -> {
                    ProbeOutcome(classifyArray(payload))
                }
                is JsonObject -> {
                    val status = classifyObject(payload)
                    val score = if (status == SessionStatus.AUTHENTICATED) extractScore(payload) else null
                    ProbeOutcome(status, score)
                }
                else -> {
                    ProbeOutcome(SessionStatus.UNKNOWN)
                }
            }
        }

        /**
         * The probe's own `status`, or null when the field is absent or JSON `null`.
         *
         * `JsonNull` is a `JsonPrimitive` whose `content` is the string `"null"`, so reading
         * `content` as a fallback would turn a null status into a status literally called "null".
         */
        fun scalarContent(obj: JsonObject?): String? {
            val primitive = obj?.get(STATUS_FIELD) as? JsonPrimitive ?: return null

            return primitive.contentOrNull?.takeIf { it.isNotEmpty() }
        }

        /**
         * First candidate field that actually carries a 学识 number.
         *
         * JSON `null`, empty strings and anything non-numeric are *skipped*, not accepted: a
         * `{"score": null, "myScore": 100}` reply has to fall through to `myScore`, and a reply
         * whose only score field is invalid has to leave the cached value alone (null = unknown)
         * instead of overwriting it with a placeholder.
         */
        private fun extractScore(obj: JsonObject): String? {
            val userinfo = obj[USERINFO_FIELD] as? JsonObject
            val candidates =
                listOf(obj[SCORE_FIELD], obj[MYSCORE_FIELD], obj[MY_SCORE_FIELD]) +
                    listOf(userinfo?.get(SCORE_FIELD), userinfo?.get(MYSCORE_FIELD), userinfo?.get(MY_SCORE_FIELD))

            return candidates.firstNotNullOfOrNull(::numericScore)
        }

        private fun numericScore(value: JsonElement?): String? {
            val primitive = value as? JsonPrimitive ?: return null
            if (primitive is JsonNull) return null
            val raw = primitive.contentOrNull?.trim().orEmpty()

            return raw.takeIf { it.toLongOrNull() != null }
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
        const val SCORE_FIELD = "score"
        const val MYSCORE_FIELD = "myscore"
        const val MY_SCORE_FIELD = "myScore"
        const val USERINFO_FIELD = "userinfo"
        const val GUEST_STATUS = "guest"
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1")
        val AUTH_EVIDENCE_FIELDS = setOf("uid", "username", "email", "tasks", USERINFO_FIELD, SCORE_FIELD)
        val NON_ACCOUNT_ENVELOPE_KEYS = setOf("message", "msg", "code", "error", "errno")
        const val PREF_KEY_STATUS = "session_status"
        const val PREF_KEY_SCORE = "score"
        const val PREF_KEY_ACCOUNT = "session_account_key"
        const val PREF_KEY_LEGACY_LOGGED_IN = "is_logged_in"
    }
}
