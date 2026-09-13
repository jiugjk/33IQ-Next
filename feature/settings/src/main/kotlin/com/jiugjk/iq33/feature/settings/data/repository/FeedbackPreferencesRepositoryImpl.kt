package com.jiugjk.iq33.feature.settings.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.settings.domain.repository.FeedbackPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shares preference keys with feed's AnswerFeedbackPreferencesImpl so toggles apply immediately.
 */
internal class FeedbackPreferencesRepositoryImpl(
    private val preferences: SharedPreferences,
) : FeedbackPreferencesRepository {
    private val animations = MutableStateFlow(preferences.getBoolean(KEY_ANIM, true))
    private val haptics = MutableStateFlow(preferences.getBoolean(KEY_HAPTIC, true))

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_ANIM -> animations.value = prefs.getBoolean(KEY_ANIM, true)
                KEY_HAPTIC -> haptics.value = prefs.getBoolean(KEY_HAPTIC, true)
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override val animationsEnabled: Flow<Boolean> = animations.asStateFlow()
    override val hapticsEnabled: Flow<Boolean> = haptics.asStateFlow()

    override fun setAnimationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ANIM, enabled) }
        animations.value = enabled
    }

    override fun setHapticsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HAPTIC, enabled) }
        haptics.value = enabled
    }

    private companion object {
        // Keep in sync with AnswerFeedbackPreferencesImpl.
        const val KEY_ANIM = "answer_feedback_animations"
        const val KEY_HAPTIC = "answer_feedback_haptics"
    }
}
