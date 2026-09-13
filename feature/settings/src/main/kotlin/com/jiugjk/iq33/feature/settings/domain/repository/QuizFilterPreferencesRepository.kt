package com.jiugjk.iq33.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

interface QuizFilterPreferencesRepository {
    val hideAnswered: Flow<Boolean>

    fun setHideAnswered(hide: Boolean)
}
