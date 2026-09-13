package com.jiugjk.iq33.feature.feed.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Device preferences for answer feedback. Defaults on for both.
 *
 * Animations and haptics are device settings and are deliberately shared between accounts; the
 * streak is behaviour data and is stored per account, so a second account on the same device never
 * inherits the first one's 连对 count.
 */
@Suppress("ComplexInterface")
internal interface AnswerFeedbackPreferences {
    val animationsEnabled: Flow<Boolean>
    val hapticsEnabled: Flow<Boolean>
    val currentAnimationsEnabled: Boolean
    val currentHapticsEnabled: Boolean

    /** Streak of the account that is logged in right now; 0 for guest / unknown sessions. */
    val streak: Flow<Int>
    val currentStreak: Int

    fun setAnimationsEnabled(enabled: Boolean)

    fun setHapticsEnabled(enabled: Boolean)

    fun recordCorrect(accountKey: String?): Int

    fun recordWrong(accountKey: String?)
}
