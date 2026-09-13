package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerFeedbackPreferences
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal class AnswerFeedbackPreferencesImpl(
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
) : AnswerFeedbackPreferences {
    private val animations = MutableStateFlow(preferences.getBoolean(KEY_ANIM, true))
    private val haptics = MutableStateFlow(preferences.getBoolean(KEY_HAPTIC, true))

    /** Bumped on every streak write so the account-scoped flow re-reads without a listener race. */
    private val streakRevision = MutableStateFlow(0L)

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_ANIM -> animations.value = prefs.getBoolean(KEY_ANIM, true)
                KEY_HAPTIC -> haptics.value = prefs.getBoolean(KEY_HAPTIC, true)
                else -> if (key != null && key.startsWith(KEY_STREAK_PREFIX)) streakRevision.value += 1
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // The pre-namespace streak belonged to no particular account, so it is not inherited.
        if (preferences.contains(LEGACY_KEY_STREAK)) preferences.edit { remove(LEGACY_KEY_STREAK) }
    }

    override val animationsEnabled: Flow<Boolean> = animations.asStateFlow()
    override val hapticsEnabled: Flow<Boolean> = haptics.asStateFlow()
    override val currentAnimationsEnabled: Boolean get() = animations.value
    override val currentHapticsEnabled: Boolean get() = haptics.value

    override val streak: Flow<Int> =
        combine(sessionManager.sessionFlow, streakRevision) { session, _ ->
            readStreak(session.accountKey)
        }.distinctUntilChanged()

    override val currentStreak: Int get() = readStreak(sessionManager.sessionFlow.value.accountKey)

    override fun setAnimationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ANIM, enabled) }
        animations.value = enabled
    }

    override fun setHapticsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HAPTIC, enabled) }
        haptics.value = enabled
    }

    override fun recordCorrect(accountKey: String?): Int {
        if (accountKey.isNullOrEmpty()) return 0
        val next = readStreak(accountKey) + 1
        writeStreak(accountKey, next)
        return next
    }

    override fun recordWrong(accountKey: String?) {
        if (accountKey.isNullOrEmpty()) return
        writeStreak(accountKey, 0)
    }

    private fun readStreak(accountKey: String?): Int = if (accountKey.isNullOrEmpty()) 0 else preferences.getInt(streakKey(accountKey), 0)

    private fun writeStreak(
        accountKey: String,
        value: Int,
    ) {
        preferences.edit { putInt(streakKey(accountKey), value) }
        streakRevision.value += 1
    }

    private fun streakKey(accountKey: String) = "$KEY_STREAK_PREFIX$accountKey"

    private companion object {
        const val KEY_ANIM = "answer_feedback_animations"
        const val KEY_HAPTIC = "answer_feedback_haptics"
        const val KEY_STREAK_PREFIX = "answer_feedback_streak:"
        const val LEGACY_KEY_STREAK = "answer_feedback_streak"
    }
}
