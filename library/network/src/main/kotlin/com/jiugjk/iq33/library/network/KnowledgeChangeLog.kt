package com.jiugjk.iq33.library.network

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class KnowledgeChangeLog(
    private val preferences: SharedPreferences,
) {
    private val entries = MutableStateFlow(load())

    val recent: Flow<List<KnowledgeChangeEntry>> = entries.asStateFlow()

    fun current(): List<KnowledgeChangeEntry> = entries.value

    fun append(
        questionId: Long,
        delta: Int,
        title: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        if (delta == 0) return
        val next =
            (listOf(KnowledgeChangeEntry(questionId, title, delta, at)) + entries.value)
                .take(MAX_ENTRIES)
        preferences.edit { putString(KEY, Json.encodeToString(next)) }
        entries.value = next
    }

    fun clear() {
        preferences.edit { remove(KEY) }
        entries.value = emptyList()
    }

    private fun load(): List<KnowledgeChangeEntry> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<KnowledgeChangeEntry>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "knowledge_delta_log"
        const val MAX_ENTRIES = 30
    }
}
