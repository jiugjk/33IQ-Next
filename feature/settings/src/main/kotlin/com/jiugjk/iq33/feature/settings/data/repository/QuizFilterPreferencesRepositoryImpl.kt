package com.jiugjk.iq33.feature.settings.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.settings.domain.repository.QuizFilterPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shares [KEY_HIDE] with feed's QuestionProgressRepositoryImpl. */
internal class QuizFilterPreferencesRepositoryImpl(
    private val preferences: SharedPreferences,
) : QuizFilterPreferencesRepository {
    private val hide = MutableStateFlow(preferences.getBoolean(KEY_HIDE, false))

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_HIDE) hide.value = prefs.getBoolean(KEY_HIDE, false)
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override val hideAnswered: Flow<Boolean> = hide.asStateFlow()

    override fun setHideAnswered(hideAnswered: Boolean) {
        preferences.edit { putBoolean(KEY_HIDE, hideAnswered) }
        hide.value = hideAnswered
    }

    private companion object {
        const val KEY_HIDE = "feed_hide_answered"
    }
}
