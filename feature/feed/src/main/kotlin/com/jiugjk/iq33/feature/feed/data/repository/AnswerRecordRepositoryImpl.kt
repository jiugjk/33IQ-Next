package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordDao
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordEntity
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Room-backed answer history with an in-memory snapshot for synchronous feed reads.
 *
 * Prefs string-sets are migrated once: `answered` → answeredAt set / outcome null;
 * `answerViewed` → viewedExplanation=true with answeredAt left null.
 */
@Suppress("TooManyFunctions")
internal class AnswerRecordRepositoryImpl(
    private val dao: AnswerRecordDao,
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AnswerRecordRepository {
    private val snapshot = MutableStateFlow<List<AnswerRecord>>(emptyList())
    private val migrated = AtomicBoolean(false)

    init {
        ensureMigrated()
        ioScope.launch {
            dao.observeAll().collect { entities ->
                snapshot.value = entities.map { it.toDomain() }
            }
        }
    }

    override val records: Flow<List<AnswerRecord>> = snapshot

    override fun current(accountKey: String?): List<AnswerRecord> {
        ensureMigrated()
        if (accountKey == null) return emptyList()
        return snapshot.value.filter { it.accountKey == accountKey }
    }

    override fun get(
        accountKey: String?,
        questionId: Long,
    ): AnswerRecord? {
        ensureMigrated()
        if (accountKey == null) return null
        return snapshot.value.firstOrNull { it.accountKey == accountKey && it.questionId == questionId }
    }

    override fun answeredIds(accountKey: String?): Set<Long> =
        current(accountKey)
            .filter { it.answeredAt != null || it.isCorrect != null || it.selectedOption != null }
            .map { it.questionId }
            .toSet()

    override fun viewedExplanationIds(accountKey: String?): Set<Long> =
        current(accountKey).filter { it.viewedExplanation }.map { it.questionId }.toSet()

    override fun viewedHintIds(accountKey: String?): Set<Long> = current(accountKey).filter { it.viewedHint }.map { it.questionId }.toSet()

    @Synchronized
    override fun recordAnswer(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
        selectedOption: String?,
        isCorrect: Boolean?,
        knowledgeDelta: Int?,
        answeredAt: Long,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        val existing = get(accountKey, questionId)
        val next =
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
                selectedOption = selectedOption ?: existing?.selectedOption,
                isCorrect = isCorrect ?: existing?.isCorrect,
                correctOption = if (isCorrect == true) selectedOption ?: existing?.correctOption else existing?.correctOption,
                knowledgeDelta = knowledgeDelta ?: existing?.knowledgeDelta,
                answeredAt = answeredAt,
                updatedAt = System.currentTimeMillis(),
            )
        persist(next)
    }

    @Synchronized
    override fun recordExplanationViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
        correctOption: String?,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        val existing = get(accountKey, questionId)
        val next =
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
                viewedExplanation = true,
                correctOption = correctOption?.takeIf { it.isNotBlank() } ?: existing?.correctOption,
                // Do not invent an answeredAt — explanation-only history stays distinct.
                updatedAt = System.currentTimeMillis(),
            )
        persist(next)
    }

    @Synchronized
    override fun recordHintViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        val existing = get(accountKey, questionId)
        val next =
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
                viewedHint = true,
                updatedAt = System.currentTimeMillis(),
            )
        persist(next)
    }

    @Synchronized
    override fun clearAnswerState(
        accountKey: String?,
        questionId: Long,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        val existing = get(accountKey, questionId) ?: return
        persist(
            existing.copy(
                selectedOption = null,
                isCorrect = null,
                answeredAt = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    override fun delete(
        accountKey: String?,
        questionId: Long,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        snapshot.update { list -> list.filterNot { it.accountKey == accountKey && it.questionId == questionId } }
        ioScope.launch { dao.delete(accountKey, questionId) }
    }

    @Synchronized
    override fun clearAll(accountKey: String?) {
        if (accountKey == null || !canWrite(accountKey)) return
        ensureMigrated()
        snapshot.update { list -> list.filterNot { it.accountKey == accountKey } }
        ioScope.launch { dao.clearAccount(accountKey) }
    }

    private fun persist(record: AnswerRecord) {
        snapshot.update { list ->
            val without = list.filterNot { it.accountKey == record.accountKey && it.questionId == record.questionId }
            without + record
        }
        ioScope.launch { dao.upsert(record.toEntity()) }
    }

    private fun canWrite(accountKey: String): Boolean = accountKey == sessionManager.sessionFlow.value.accountKey

    @Suppress("CyclomaticComplexMethod")
    private fun ensureMigrated() {
        if (!migrated.compareAndSet(false, true)) return
        if (preferences.getBoolean(MIGRATION_DONE, false)) {
            // Cold start before Flow emits: pull once.
            runBlocking {
                snapshot.value = dao.getAll().map { it.toDomain() }
            }
            return
        }
        val now = System.currentTimeMillis()
        val migratedRecords = linkedMapOf<String, AnswerRecord>()

        fun keyOf(
            account: String,
            id: Long,
        ) = "$account::$id"

        preferences.all.forEach { (key, value) ->
            when {
                key.startsWith(ANSWERED_PREFIX) && value is Set<*> -> {
                    val account = key.removePrefix(ANSWERED_PREFIX)
                    value.mapNotNull { it as? String }.mapNotNull { it.toLongOrNull() }.forEach { id ->
                        val mapKey = keyOf(account, id)
                        val existing = migratedRecords[mapKey]
                        migratedRecords[mapKey] =
                            (existing ?: AnswerRecord(questionId = id, accountKey = account)).copy(
                                answeredAt = existing?.answeredAt ?: now,
                                updatedAt = now,
                            )
                    }
                }
                key.startsWith(ANSWER_VIEWED_PREFIX) && value is Set<*> -> {
                    val account = key.removePrefix(ANSWER_VIEWED_PREFIX)
                    value.mapNotNull { it as? String }.mapNotNull { it.toLongOrNull() }.forEach { id ->
                        val mapKey = keyOf(account, id)
                        val existing = migratedRecords[mapKey]
                        migratedRecords[mapKey] =
                            (existing ?: AnswerRecord(questionId = id, accountKey = account)).copy(
                                viewedExplanation = true,
                                // Keep answeredAt as-is (null when explanation-only).
                                answeredAt = existing?.answeredAt,
                                updatedAt = now,
                            )
                    }
                }
            }
        }

        if (migratedRecords.isNotEmpty()) {
            snapshot.value = migratedRecords.values.toList()
            runBlocking { dao.upsertAll(migratedRecords.values.map { it.toEntity() }) }
        } else {
            runBlocking { snapshot.value = dao.getAll().map { it.toDomain() } }
        }

        preferences.edit {
            putBoolean(MIGRATION_DONE, true)
            preferences.all.keys
                .filter { it.startsWith(ANSWERED_PREFIX) || it.startsWith(ANSWER_VIEWED_PREFIX) }
                .forEach { remove(it) }
        }
    }

    private companion object {
        const val MIGRATION_DONE = "answer_records_migrated_v1"
        const val ANSWERED_PREFIX = "answered:"
        const val ANSWER_VIEWED_PREFIX = "answerViewed:"
    }
}

private fun AnswerRecordEntity.toDomain() =
    AnswerRecord(
        questionId = questionId,
        accountKey = accountKey,
        title = title,
        categoryId = categoryId,
        selectedOption = selectedOption,
        isCorrect = isCorrect,
        correctOption = correctOption,
        viewedExplanation = viewedExplanation,
        viewedHint = viewedHint,
        knowledgeDelta = knowledgeDelta,
        answeredAt = answeredAt,
        updatedAt = updatedAt,
    )

private fun AnswerRecord.toEntity() =
    AnswerRecordEntity(
        accountKey = accountKey,
        questionId = questionId,
        title = title,
        categoryId = categoryId,
        selectedOption = selectedOption,
        isCorrect = isCorrect,
        correctOption = correctOption,
        viewedExplanation = viewedExplanation,
        viewedHint = viewedHint,
        knowledgeDelta = knowledgeDelta,
        answeredAt = answeredAt,
        updatedAt = updatedAt,
    )
