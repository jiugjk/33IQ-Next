package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Feed prefs (hide filter, pending reveal latch, pagination) plus answered/viewed IDs derived from
 * [AnswerRecordRepository]. Answer writes must go through AnswerRecordRepository's three entry points.
 */
internal class QuestionProgressRepositoryImpl(
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
    private val answerRecords: AnswerRecordRepository,
) : QuestionProgressRepository {
    private val revision = MutableStateFlow(0L)

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HIDE_ANSWERED) revision.update { it + 1 }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override val current: QuestionProgress
        get() = readProgress(sessionManager.sessionFlow.value.accountKey)

    override val progress =
        combine(sessionManager.sessionFlow, revision, answerRecords.records) { session, _, _ ->
            readProgress(session.accountKey)
        }.distinctUntilChanged()

    @Synchronized
    override fun setAnswerRevealPending(
        questionId: Long,
        pending: Boolean,
        accountKey: String?,
    ): Boolean {
        if (accountKey == null || current.accountKey != accountKey) return false
        val key = "answerPending:$accountKey"
        val old = preferences.getStringSet(key, emptySet()).orEmpty()
        val ids = if (pending) old + questionId.toString() else old - questionId.toString()
        val saved = preferences.edit().putStringSet(key, ids).commit()
        if (!saved) preferences.edit().putStringSet(key, old).apply()
        revision.update { it + 1 }
        return saved
    }

    override fun setHideAnswered(hide: Boolean) {
        preferences.edit { putBoolean(HIDE_ANSWERED, hide) }
        revision.update { it + 1 }
    }

    override fun feedPosition(categoryId: String): FeedPosition {
        val key = feedKey(categoryId, current.accountKey)
        return FeedPosition(
            nextPageUrl = preferences.getString(nextKey(key), null),
            lastQuestionIds =
                preferences
                    .getString(recentKey(key), null)
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.toLongOrNull() }
                    .toSet(),
        )
    }

    override fun saveFeedPosition(
        categoryId: String,
        position: FeedPosition,
        accountKey: String?,
    ) {
        if (current.accountKey != accountKey) return
        val key = feedKey(categoryId, accountKey)
        preferences.edit {
            putString(nextKey(key), position.nextPageUrl)
            putString(recentKey(key), position.lastQuestionIds.joinToString(","))
        }
    }

    override fun clearFeedPosition(
        categoryId: String,
        accountKey: String?,
    ) {
        if (current.accountKey != accountKey) return
        val key = feedKey(categoryId, accountKey)
        preferences.edit {
            remove(nextKey(key))
            remove(recentKey(key))
        }
    }

    private fun readProgress(accountKey: String?) =
        QuestionProgress(
            accountKey = accountKey,
            answeredIds = answerRecords.answeredIds(accountKey),
            hideAnswered = preferences.getBoolean(HIDE_ANSWERED, false),
            viewedAnswerIds = answerRecords.viewedExplanationIds(accountKey),
            pendingAnswerRevealIds = if (accountKey == null) emptySet() else readIds("answerPending:$accountKey"),
        )

    private fun readIds(key: String): Set<Long> =
        preferences
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private fun feedKey(
        categoryId: String,
        accountKey: String?,
    ) = "feed:${accountKey ?: "guest"}:$categoryId"

    private fun nextKey(key: String) = "$key:next"

    private fun recentKey(key: String) = "$key:recent"

    private companion object {
        const val HIDE_ANSWERED = "feed_hide_answered"
    }
}
