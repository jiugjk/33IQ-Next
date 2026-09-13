package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/** Stores IDs only, never submitted answers, cookies or question bodies. */
internal class QuestionProgressRepositoryImpl(
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
) : QuestionProgressRepository {
    private val revision = MutableStateFlow(0L)

    override val current: QuestionProgress
        get() = readProgress(sessionManager.sessionFlow.value.accountKey)

    override val progress =
        combine(sessionManager.sessionFlow, revision) { session, _ ->
            readProgress(session.accountKey)
        }.distinctUntilChanged()

    @Synchronized
    override fun recordAnswered(
        questionId: Long,
        accountKey: String?,
    ) {
        recordId("answered", questionId, accountKey)
    }

    @Synchronized
    override fun recordAnswerViewed(
        questionId: Long,
        accountKey: String?,
    ) {
        recordId("answerViewed", questionId, accountKey)
    }

    private fun recordId(
        kind: String,
        questionId: Long,
        accountKey: String?,
    ) {
        if (accountKey == null || current.accountKey != accountKey) return
        val key = "$kind:$accountKey"
        val ids = preferences.getStringSet(key, emptySet()).orEmpty() + questionId.toString()
        preferences.edit { putStringSet(key, ids) }
        revision.update { it + 1 }
    }

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
        // Android commits update memory even on disk failure. Restore the old latch in that case.
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
            nextPageUrl = preferences.getString("$key:next", null),
            // SharedPreferences string sets have no iteration order. Preserve recency explicitly.
            lastQuestionIds =
                preferences
                    .getString("$key:recent", null)
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
            putString("$key:next", position.nextPageUrl)
            putString("$key:recent", position.lastQuestionIds.joinToString(","))
        }
    }

    private fun readProgress(accountKey: String?) =
        QuestionProgress(
            accountKey = accountKey,
            answeredIds = if (accountKey == null) emptySet() else readIds("answered:$accountKey"),
            hideAnswered = preferences.getBoolean(HIDE_ANSWERED, false),
            viewedAnswerIds = if (accountKey == null) emptySet() else readIds("answerViewed:$accountKey"),
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

    private companion object {
        const val HIDE_ANSWERED = "feed_hide_answered"
    }
}
