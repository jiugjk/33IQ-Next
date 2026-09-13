package com.jiugjk.iq33.feature.feed.domain.repository

import kotlinx.coroutines.flow.Flow

/** Device preferences for answer feedback. Defaults on for both. */
internal interface AnswerFeedbackPreferences {
    val animationsEnabled: Flow<Boolean>
    val hapticsEnabled: Flow<Boolean>
    val currentAnimationsEnabled: Boolean
    val currentHapticsEnabled: Boolean
    val streak: Flow<Int>
    val currentStreak: Int

    fun setAnimationsEnabled(enabled: Boolean)

    fun setHapticsEnabled(enabled: Boolean)

    fun recordCorrect(): Int

    fun recordWrong()
}
