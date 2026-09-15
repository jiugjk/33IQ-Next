package com.jiugjk.iq33.feature.feed.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordDao
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordEntity
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.library.network.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Room-backed answer history with an in-memory snapshot for synchronous feed reads.
 *
 * Room is the single source of truth. Every mutation is expressed as a read-modify-write function
 * and handed to one serial worker, which applies it to the row *as stored* and writes the result
 * back; commands can therefore never overtake each other, and no writer ever persists a stale copy
 * of a row it read minutes ago.
 *
 * Callers still need an answer immediately (the feed's hide-filter reads this synchronously), so a
 * mutation is also applied optimistically to a pending overlay. The overlay - not the database
 * notification - wins until that command has actually been written, which is what stops an older
 * `observeAll` emission from resurrecting a deleted row or dropping a field that is still in flight.
 * A write that fails drops its overlay entry, so the exposed state falls back to what Room holds.
 *
 * Nothing here ever blocks the caller's thread. The synchronous reads answer from the snapshot as it
 * stands, which before the first load is empty - "not known yet", the same thing an absent row has
 * always meant here. [records] emits again the moment it is, and every read path in the app is
 * driven off that flow.
 *
 * Prefs string-sets are migrated once: `answered` → answeredAt set / outcome null;
 * `answerViewed` → viewedExplanation=true with answeredAt left null. The migration runs ahead of
 * both the command worker and the `observeAll` subscription, on the one coroutine that owns them:
 * a command applied before it, or an emission from the not-yet-migrated table, would write the
 * migration's own rows back out of existence.
 */
@Suppress("TooManyFunctions")
internal class AnswerRecordRepositoryImpl(
    private val dao: AnswerRecordDao,
    private val preferences: SharedPreferences,
    private val sessionManager: SessionManager,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AnswerRecordRepository {
    /** Last state read from Room. */
    private val stored = MutableStateFlow<Map<RecordKey, AnswerRecord>>(emptyMap())

    /** Mutations that have been accepted but not yet written; these override [stored]. */
    private val pending = MutableStateFlow<Map<RecordKey, PendingWrite>>(emptyMap())

    /** Accounts with an in-flight clear-all, so a stale Room emission cannot restore their rows. */
    private val clearing = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val snapshot = MutableStateFlow<List<AnswerRecord>>(emptyList())
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val sequence = AtomicLong(0)
    private val migrated = AtomicBoolean(false)

    init {
        ioScope.launch {
            // A migration that fails must not take the worker down with it: the command channel has
            // no other consumer, so a dead worker would swallow every write for the whole process.
            // MIGRATION_DONE is latched only after a clean run, so the next start tries again.
            try {
                runMigrationAndLoad()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                Timber.tag(TimberLogTags.DATABASE).w(error, "Answer-record migration failed")
            }
            launch {
                dao.observeAll().collect { entities ->
                    stored.value = entities.associate { RecordKey(it.accountKey, it.questionId) to it.toDomain() }
                    publish()
                }
            }
            for (command in commands) execute(command)
        }
    }

    override val records: Flow<List<AnswerRecord>> = snapshot

    override fun current(accountKey: String?): List<AnswerRecord> {
        if (accountKey == null) return emptyList()
        return snapshot.value.filter { it.accountKey == accountKey }
    }

    override fun get(
        accountKey: String?,
        questionId: Long,
    ): AnswerRecord? {
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

    @Suppress("LongParameterList")
    override fun recordAnswer(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
        selectedOption: String?,
        isCorrect: Boolean?,
        knowledgeDelta: Int?,
        answeredAt: Long,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
            selectedOption = selectedOption ?: existing?.selectedOption,
            isCorrect = isCorrect ?: existing?.isCorrect,
            correctOption = if (isCorrect == true) selectedOption ?: existing?.correctOption else existing?.correctOption,
            knowledgeDelta = knowledgeDelta ?: existing?.knowledgeDelta,
            answeredAt = answeredAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Suppress("LongParameterList")
    override fun recordExplanationViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
        correctOption: String?,
        explanationText: String?,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
            viewedExplanation = true,
            correctOption = correctOption?.takeIf { it.isNotBlank() } ?: existing?.correctOption,
            explanationText = explanationText?.takeIf { it.isNotBlank() } ?: existing?.explanationText,
            // Do not invent an answeredAt — explanation-only history stays distinct.
            updatedAt = System.currentTimeMillis(),
        )
    }

    override fun recordHintViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
        hintText: String?,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryId = categoryId.ifBlank { existing?.categoryId.orEmpty() },
            viewedHint = true,
            hintText = hintText?.takeIf { it.isNotBlank() } ?: existing?.hintText,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override fun updateMetadata(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
    ) {
        if (title.isBlank() && categoryId.isBlank()) return
        val known = get(accountKey, questionId) ?: return
        val titleChanged = title.isNotBlank() && title != known.title
        val categoryChanged = categoryId.isNotBlank() && categoryId != known.categoryId
        if (!titleChanged && !categoryChanged) return

        // Metadata alone never creates a record: visiting a question is not progress. updatedAt is
        // left untouched so backfilling a title does not reshuffle the history list.
        mutate(accountKey, questionId) { existing ->
            existing?.copy(
                title = title.ifBlank { existing.title },
                categoryId = categoryId.ifBlank { existing.categoryId },
            )
        }
    }

    override fun delete(
        accountKey: String?,
        questionId: Long,
    ) = mutate(accountKey, questionId) { null }

    @Synchronized
    override fun clearAll(accountKey: String?) {
        if (accountKey == null || !canWrite(accountKey)) return
        clearing.update { it + (accountKey to (it[accountKey] ?: 0) + 1) }
        // Optimistic overlay entries for this account are superseded by the clear.
        pending.update { map -> map.filterKeys { it.accountKey != accountKey } }
        publish()
        commands.trySend(Command.ClearAccount(accountKey))
    }

    /**
     * Applies [transform] optimistically and queues the same transform for Room.
     *
     * `@Synchronized` here only guards the in-memory bookkeeping; ordering against the database is
     * provided by the single-consumer command channel, not by this lock.
     */
    @Synchronized
    private fun mutate(
        accountKey: String?,
        questionId: Long,
        transform: (AnswerRecord?) -> AnswerRecord?,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        val key = RecordKey(accountKey, questionId)
        val seq = sequence.incrementAndGet()
        val optimistic = transform(currentRecord(key))
        pending.update { it + (key to PendingWrite(seq, optimistic)) }
        publish()
        commands.trySend(Command.Mutate(key, seq, transform))
    }

    private suspend fun execute(command: Command) {
        try {
            when (command) {
                is Command.Mutate -> {
                    executeMutation(command)
                }
                is Command.ClearAccount -> {
                    dao.clearAccount(command.accountKey)
                    clearing.update { map ->
                        val left = (map[command.accountKey] ?: 1) - 1
                        if (left <= 0) map - command.accountKey else map + (command.accountKey to left)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            // The optimistic overlay is dropped below, so the exposed state falls back to Room's.
            Timber.tag(TimberLogTags.DATABASE).w(error, "Answer-record write failed: %s", command)
            if (command is Command.Mutate) releasePending(command.key, command.sequence)
        } finally {
            publish()
        }
    }

    /** Read-modify-write against the stored row: the queued transform never carries a stale copy. */
    private suspend fun executeMutation(command: Command.Mutate) {
        val key = command.key
        val existing = dao.get(key.accountKey, key.questionId)?.toDomain()
        val next = command.transform(existing)

        when {
            next == null && existing != null -> dao.delete(key.accountKey, key.questionId)
            next != null -> dao.upsert(next.toEntity())
            else -> Unit
        }
        releasePending(key, command.sequence)
    }

    /** Drops the overlay entry once its own write finished; a newer pending write stays in place. */
    private fun releasePending(
        key: RecordKey,
        sequence: Long,
    ) {
        pending.update { map -> if (map[key]?.sequence == sequence) map - key else map }
    }

    private fun currentRecord(key: RecordKey): AnswerRecord? {
        pending.value[key]?.let { return it.record }
        if (clearing.value.containsKey(key.accountKey)) return null
        return stored.value[key]
    }

    private fun publish() {
        val cleared = clearing.value
        val overlay = pending.value
        val merged = LinkedHashMap<RecordKey, AnswerRecord>()
        stored.value.forEach { (key, record) ->
            if (!cleared.containsKey(key.accountKey)) merged[key] = record
        }
        overlay.forEach { (key, write) ->
            if (write.record == null) merged.remove(key) else merged[key] = write.record
        }
        snapshot.value = merged.values.sortedByDescending { it.updatedAt }
    }

    private fun canWrite(accountKey: String): Boolean = accountKey == sessionManager.sessionFlow.value.accountKey

    private fun newRecord(
        existing: AnswerRecord?,
        accountKey: String,
        questionId: Long,
    ) = existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)

    /**
     * Migrates the prefs string-sets once, then publishes what the table holds.
     *
     * Runs on [ioScope] before the command worker starts consuming and before `observeAll` is
     * subscribed, so no write can be applied to - and no emission can be taken from - a table the
     * migration has not finished writing.
     */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun runMigrationAndLoad() {
        if (!migrated.compareAndSet(false, true)) return
        if (preferences.getBoolean(MIGRATION_DONE, false)) {
            // Cold start before Flow emits: pull once.
            loadStored()
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
            dao.upsertAll(migratedRecords.values.map { it.toEntity() })
        }
        loadStored()

        preferences.edit {
            putBoolean(MIGRATION_DONE, true)
            preferences.all.keys
                .filter { it.startsWith(ANSWERED_PREFIX) || it.startsWith(ANSWER_VIEWED_PREFIX) }
                .forEach { remove(it) }
        }
    }

    private suspend fun loadStored() {
        stored.value = dao.getAll().associate { RecordKey(it.accountKey, it.questionId) to it.toDomain() }
        publish()
    }

    private data class RecordKey(
        val accountKey: String,
        val questionId: Long,
    )

    private data class PendingWrite(
        val sequence: Long,
        /** Null means "deleted"; the row must not come back when Room replays an older list. */
        val record: AnswerRecord?,
    )

    private sealed interface Command {
        data class Mutate(
            val key: RecordKey,
            val sequence: Long,
            val transform: (AnswerRecord?) -> AnswerRecord?,
        ) : Command

        data class ClearAccount(
            val accountKey: String,
        ) : Command
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
        hintText = hintText,
        explanationText = explanationText,
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
        hintText = hintText,
        explanationText = explanationText,
        knowledgeDelta = knowledgeDelta,
        answeredAt = answeredAt,
        updatedAt = updatedAt,
    )
