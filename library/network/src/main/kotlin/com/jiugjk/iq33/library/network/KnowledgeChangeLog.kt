package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Recent local 学识 deltas for the settings expand UI. Not a server truth source. */
@Serializable
data class KnowledgeChangeEntry(
    val questionId: Long,
    val title: String = "",
    val delta: Int,
    val at: Long,
    /** Account this delta belongs to; entries are never shown to another account. */
    val accountKey: String = "",
)

/**
 * Per-account 学识 change log.
 *
 * Every entry is stored under its own account's key, so a session that expires (or a second account
 * logging in on the same device) reads an empty log rather than the previous account's history -
 * without depending on the settings screen's logout button ever being pressed.
 */
class KnowledgeChangeLog(
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
) {
    private val revision = MutableStateFlow(0L)

    init {
        // The pre-namespace log cannot be attributed to an account, so it is dropped instead of
        // being shown to whoever logs in next.
        if (preferences.contains(LEGACY_KEY)) preferences.edit { remove(LEGACY_KEY) }
    }

    /** Entries of the account that is logged in right now; empty for guest / unknown sessions. */
    val recent: Flow<List<KnowledgeChangeEntry>> =
        combine(sessionManager.sessionFlow, revision) { session, _ ->
            load(session.accountKey)
        }.distinctUntilChanged()

    fun current(accountKey: String? = sessionManager.sessionFlow.value.accountKey): List<KnowledgeChangeEntry> = load(accountKey)

    fun append(
        accountKey: String?,
        questionId: Long,
        delta: Int,
        title: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        if (delta == 0 || accountKey.isNullOrEmpty()) return
        val entry = KnowledgeChangeEntry(questionId, title, delta, at, accountKey)
        val next = (listOf(entry) + load(accountKey)).take(MAX_ENTRIES)
        preferences.edit { putString(keyFor(accountKey), Json.encodeToString(next)) }
        revision.value += 1
    }

    /**
     * Fills in the title of this account's entries for [questionId] that were written without one.
     *
     * An entry is appended from the answer-submission path, which is handed whatever label the
     * caller had - a feed row that never loaded the question detail has none. The screen learns the
     * real one a moment later and backfills it here, so the settings list names the question instead
     * of showing a bare number. Entries that already carry a title are left exactly as they are.
     */
    fun backfillTitle(
        accountKey: String?,
        questionId: Long,
        title: String,
    ) {
        if (accountKey.isNullOrEmpty() || title.isBlank()) return
        val entries = load(accountKey)
        if (entries.none { it.questionId == questionId && it.title.isBlank() }) return
        val patched = entries.map { if (it.questionId == questionId && it.title.isBlank()) it.copy(title = title) else it }
        preferences.edit { putString(keyFor(accountKey), Json.encodeToString(patched)) }
        revision.value += 1
    }

    fun clear(accountKey: String? = sessionManager.sessionFlow.value.accountKey) {
        if (accountKey.isNullOrEmpty()) return
        preferences.edit { remove(keyFor(accountKey)) }
        revision.value += 1
    }

    private fun load(accountKey: String?): List<KnowledgeChangeEntry> {
        if (accountKey.isNullOrEmpty()) return emptyList()
        val raw = preferences.getString(keyFor(accountKey), null) ?: return emptyList()

        return runCatching { Json.decodeFromString<List<KnowledgeChangeEntry>>(raw) }
            .getOrDefault(emptyList())
            // Defensive: a stored entry that names another account is never surfaced here.
            .filter { it.accountKey.isEmpty() || it.accountKey == accountKey }
    }

    private fun keyFor(accountKey: String) = "$LEGACY_KEY:$accountKey"

    private companion object {
        const val LEGACY_KEY = "knowledge_delta_log"
        const val MAX_ENTRIES = 30
    }
}
