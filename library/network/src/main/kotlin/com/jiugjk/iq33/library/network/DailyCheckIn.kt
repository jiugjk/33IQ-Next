package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * How one daily-check-in HTTP call turned out.
 *
 * The capture that documented these endpoints only asserted HTTP 200, so success is "not an error
 * envelope" rather than a confirmed `status` string.
 */
sealed interface CheckInStepResult {
    data object Success : CheckInStepResult

    data object NotLoggedIn : CheckInStepResult

    data class Failed(
        val reason: String?,
    ) : CheckInStepResult
}

sealed interface DailyCheckInResult {
    data object SkippedAlreadyDone : DailyCheckInResult

    data class Ran(
        val lottery: CheckInStepResult,
        val signIn: CheckInStepResult,
    ) : DailyCheckInResult
}

/**
 * Claims 33IQ's daily check-in for the current session, at most once per calendar day.
 *
 * Endpoints and form fields come from a captured logged-in *web* session (not the official Android
 * app HAR): `POST /member/gettask` with `tasktype=lottery&reason=`, then `POST /index/signin` with
 * an empty body. This client does not add the action-endpoint `p=1` suffix those other calls need -
 * the capture did not send it.
 *
 * The calendar day is Asia/Shanghai, which is 33IQ's own timezone. A local mark is stored so a
 * launch later the same day does not hit the server again; [clearLocalMark] drops it on logout so
 * a different account logging in the same day can still claim.
 */
class DailyCheckIn(
    private val htmlClient: IqHtmlClient,
    private val preferences: SharedPreferences,
    private val clock: Clock = Clock.system(SHANGHAI),
) {
    private val mutex = Mutex()

    suspend fun runIfDue(): DailyCheckInResult =
        mutex.withLock {
            val today = today()

            if (preferences.getString(PREF_LAST_DATE, null) == today) {
                DailyCheckInResult.SkippedAlreadyDone
            } else {
                val lottery = postStep(IqConstants.DAILY_TASK_URL, LOTTERY_PARAMS)
                val signIn = postStep(IqConstants.SIGN_IN_URL, emptyMap())

                if (signIn is CheckInStepResult.Success) {
                    preferences.edit { putString(PREF_LAST_DATE, today) }
                }

                Timber.tag(LOG_TAG).d("Daily check-in lottery=%s signIn=%s", lottery, signIn)

                DailyCheckInResult.Ran(lottery = lottery, signIn = signIn)
            }
        }

    suspend fun clearLocalMark() {
        mutex.withLock {
            preferences.edit { remove(PREF_LAST_DATE) }
        }
    }

    private suspend fun postStep(
        url: String,
        params: Map<String, String>,
    ): CheckInStepResult =
        runCatching { classify(htmlClient.postFormForText(url, params)) }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable

            if (throwable is IqLoginRequiredException) {
                CheckInStepResult.NotLoggedIn
            } else {
                CheckInStepResult.Failed(throwable.message)
            }
        }

    private fun classify(body: String): CheckInStepResult {
        val status = statusOf(body) ?: return CheckInStepResult.Success

        return when {
            status.lowercase() in NOT_LOGGED_IN_STATUSES -> CheckInStepResult.NotLoggedIn
            status.lowercase() in ERROR_STATUSES -> CheckInStepResult.Failed(status)
            else -> CheckInStepResult.Success
        }
    }

    private fun statusOf(body: String): String? {
        val obj = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val primitive = obj[STATUS_FIELD] as? JsonPrimitive ?: return null

        return primitive.contentOrNull ?: primitive.content.takeIf { it.isNotEmpty() }
    }

    private fun today(): String = LocalDate.now(clock.withZone(SHANGHAI)).toString()

    private companion object {
        const val LOG_TAG = "Network"
        const val STATUS_FIELD = "status"
        const val PREF_LAST_DATE = "daily_check_in_date"
        val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
        val LOTTERY_PARAMS = mapOf("tasktype" to "lottery", "reason" to "")
        val NOT_LOGGED_IN_STATUSES = setOf("nologin", "guest")
        val ERROR_STATUSES = setOf("error", "fail", "failed", "false", "0", "-1")
    }
}
