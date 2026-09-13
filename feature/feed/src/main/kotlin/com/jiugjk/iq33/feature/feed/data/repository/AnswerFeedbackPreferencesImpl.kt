package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerFeedbackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AnswerFeedbackPreferencesImpl(
    private val preferences: SharedPreferences,
) : AnswerFeedbackPreferences {
    private val animations = MutableStateFlow(preferences.getBoolean(KEY_ANIM, true))
    private val haptics = MutableStateFlow(preferences.getBoolean(KEY_HAPTIC, true))
    private val streakFlow = MutableStateFlow(preferences.getInt(KEY_STREAK, 0))

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_ANIM -> animations.value = prefs.getBoolean(KEY_ANIM, true)
                KEY_HAPTIC -> haptics.value = prefs.getBoolean(KEY_HAPTIC, true)
                KEY_STREAK -> streakFlow.value = prefs.getInt(KEY_STREAK, 0)
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override val animationsEnabled: Flow<Boolean> = animations.asStateFlow()
    override val hapticsEnabled: Flow<Boolean> = haptics.asStateFlow()
    override val currentAnimationsEnabled: Boolean get() = animations.value
    override val currentHapticsEnabled: Boolean get() = haptics.value
    override val streak: Flow<Int> = streakFlow.asStateFlow()
    override val currentStreak: Int get() = streakFlow.value

    override fun setAnimationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ANIM, enabled) }
        animations.value = enabled
    }

    override fun setHapticsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HAPTIC, enabled) }
        haptics.value = enabled
    }

    override fun recordCorrect(): Int {
        val next = currentStreak + 1
        preferences.edit { putInt(KEY_STREAK, next) }
        streakFlow.value = next
        return next
    }

    override fun recordWrong() {
        preferences.edit { putInt(KEY_STREAK, 0) }
        streakFlow.value = 0
    }

    private companion object {
        const val KEY_ANIM = "answer_feedback_animations"
        const val KEY_HAPTIC = "answer_feedback_haptics"
        const val KEY_STREAK = "answer_feedback_streak"
    }
}
