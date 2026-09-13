package com.jiugjk.iq33.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

interface FeedbackPreferencesRepository {
    val animationsEnabled: Flow<Boolean>
    val hapticsEnabled: Flow<Boolean>

    fun setAnimationsEnabled(enabled: Boolean)

    fun setHapticsEnabled(enabled: Boolean)
}
