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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
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

    /** Unconfirmed commit timestamps / tombstones to protect against stale observation emissions. */
    private val lastCommittedUpdatedAt = ConcurrentHashMap<RecordKey, Long>()

    private val snapshot = MutableStateFlow<List<AnswerRecord>>(emptyList())
    private val indexedSnapshot = MutableStateFlow(RepositorySnapshot())

    override val records: Flow<List<AnswerRecord>> = snapshot

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val sequence = AtomicLong(0)
    private val migrated = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    private val initializedState = MutableStateFlow(false)
    val isInitialized: Flow<Boolean> = initializedState.asStateFlow()

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
            } finally {
                initializedState.value = true
            }

            supervisorScope {
                launch {
                    startObserving()
                }
                launch {
                    startConsumingCommands()
                }
            }
        }
    }

    private suspend fun startObserving() {
        while (currentCoroutineContext().isActive && !isClosed.get()) {
            try {
                dao.observeAll().collect { entities ->
                    onDatabaseObserved(entities)
                }
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                Timber.tag(TimberLogTags.DATABASE).w(error, "Answer-record database observation failed, retrying...")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun onDatabaseObserved(entities: List<AnswerRecordEntity>) {
        val observedMap = entities.associateBy { RecordKey(it.accountKey, it.questionId) }
        cleanupAcknowledgedTombstones(observedMap)

        val newStored = HashMap<RecordKey, AnswerRecord>()
        for (entity in entities) {
            val key = RecordKey(entity.accountKey, entity.questionId)
            val committedTime = lastCommittedUpdatedAt[key]
            when {
                committedTime == -1L -> {
                    // Deleted record that Room's older emission still contains: ignore
                }
                committedTime != null && entity.updatedAt < committedTime -> {
                    // Stale entity from before our commit: keep current stored value
                    stored.value[key]?.let { newStored[key] = it }
                }
                else -> {
                    newStored[key] = entity.toDomain()
                }
            }
        }

        // Keep any locally committed rows not yet present in this emission
        lastCommittedUpdatedAt.forEach { (key, committedTime) ->
            if (committedTime != -1L && key !in newStored) {
                stored.value[key]?.let { newStored[key] = it }
            }
        }

        stored.value = newStored
        publish()
    }

    private fun cleanupAcknowledgedTombstones(observedMap: Map<RecordKey, AnswerRecordEntity>) {
        lastCommittedUpdatedAt.forEach { (key, committedTime) ->
            if (committedTime == -1L) {
                if (key !in observedMap) {
                    lastCommittedUpdatedAt.remove(key)
                }
            } else {
                val observed = observedMap[key]
                if (observed != null && observed.updatedAt >= committedTime) {
                    lastCommittedUpdatedAt.remove(key)
                }
            }
        }
    }

    private suspend fun startConsumingCommands() {
        try {
            for (command in commands) {
                execute(command)
            }
        } finally {
            isClosed.set(true)
        }
    }

    override fun current(accountKey: String?): List<AnswerRecord> {
        if (accountKey == null) return emptyList()
        return indexedSnapshot.value.byAccount[accountKey] ?: emptyList()
    }

    override fun get(
        accountKey: String?,
        questionId: Long,
    ): AnswerRecord? {
        if (accountKey == null) return null
        return indexedSnapshot.value.byKey[RecordKey(accountKey, questionId)]
    }

    override fun answeredIds(accountKey: String?): Set<Long> {
        if (accountKey == null) return emptySet()
        return indexedSnapshot.value.answeredIdsByAccount[accountKey] ?: emptySet()
    }

    override fun viewedExplanationIds(accountKey: String?): Set<Long> {
        if (accountKey == null) return emptySet()
        return indexedSnapshot.value.viewedExplanationIdsByAccount[accountKey] ?: emptySet()
    }

    override fun viewedHintIds(accountKey: String?): Set<Long> {
        if (accountKey == null) return emptySet()
        return indexedSnapshot.value.viewedHintIdsByAccount[accountKey] ?: emptySet()
    }

    @Suppress("LongParameterList")
    override fun recordAnswer(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        selectedOption: String?,
        isCorrect: Boolean?,
        knowledgeDelta: Int?,
        answeredAt: Long,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
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
        categoryLabel: String,
        correctOption: String?,
        explanationText: String?,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
            viewedExplanation = true,
            correctOption = correctOption?.takeIf { it.isNotBlank() } ?: existing?.correctOption,
            explanationText = explanationText?.takeIf { it.isNotBlank() } ?: existing?.explanationText,
            // Do not invent an answeredAt — explanation-only history stays distinct.
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Suppress("LongParameterList")
    override suspend fun recordExplanationViewedAwait(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        correctOption: String?,
        explanationText: String?,
    ): Boolean =
        mutateAndAwait(accountKey, questionId) { existing ->
            newRecord(existing, accountKey!!, questionId).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
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
        categoryLabel: String,
        hintText: String?,
    ) = mutate(accountKey, questionId) { existing ->
        newRecord(existing, accountKey!!, questionId).copy(
            title = title.ifBlank { existing?.title.orEmpty() },
            categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
            viewedHint = true,
            hintText = hintText?.takeIf { it.isNotBlank() } ?: existing?.hintText,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun recordHintViewedAwait(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        hintText: String?,
    ): Boolean =
        mutateAndAwait(accountKey, questionId) { existing ->
            newRecord(existing, accountKey!!, questionId).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
                viewedHint = true,
                hintText = hintText?.takeIf { it.isNotBlank() } ?: existing?.hintText,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override fun updateMetadata(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
    ) {
        if (title.isBlank() && categoryLabel.isBlank()) return
        val known = get(accountKey, questionId) ?: return
        val titleChanged = title.isNotBlank() && title != known.title
        val categoryChanged = categoryLabel.isNotBlank() && categoryLabel != known.categoryLabel
        if (!titleChanged && !categoryChanged) return

        // Metadata alone never creates a record: visiting a question is not progress. updatedAt is
        // left untouched so backfilling a title does not reshuffle the history list.
        mutate(accountKey, questionId) { existing ->
            existing?.copy(
                title = title.ifBlank { existing.title },
                categoryLabel = categoryLabel.ifBlank { existing.categoryLabel },
            )
        }
    }

    override fun delete(
        accountKey: String?,
        questionId: Long,
    ) = mutate(accountKey, questionId) { null }

    @Synchronized
    override fun clearAll(accountKey: String?) {
        if (accountKey == null || !canWrite(accountKey) || isClosed.get()) return
        clearing.update { it + (accountKey to (it[accountKey] ?: 0) + 1) }
        // Optimistic overlay entries for this account are superseded by the clear.
        pending.update { map -> map.filterKeys { it.accountKey != accountKey } }
        publish()
        val sent = commands.trySend(Command.ClearAccount(accountKey, null)).isSuccess
        if (!sent) {
            clearing.update { map ->
                val left = (map[accountKey] ?: 1) - 1
                if (left <= 0) map - accountKey else map + (accountKey to left)
            }
            publish()
        }
    }

    /**
     * Applies [transform] optimistically and queues the same transform for Room.
     */
    @Synchronized
    private fun mutate(
        accountKey: String?,
        questionId: Long,
        transform: (AnswerRecord?) -> AnswerRecord?,
    ) {
        if (accountKey == null || !canWrite(accountKey) || isClosed.get()) return
        val key = RecordKey(accountKey, questionId)
        val seq = sequence.incrementAndGet()
        val optimistic = transform(currentRecord(key))
        pending.update { it + (key to PendingWrite(seq, optimistic)) }
        publish()
        val sent = commands.trySend(Command.Mutate(key, seq, transform, null)).isSuccess
        if (!sent) {
            releasePending(key, seq)
            publish()
        }
    }

    private suspend fun mutateAndAwait(
        accountKey: String?,
        questionId: Long,
        transform: (AnswerRecord?) -> AnswerRecord?,
    ): Boolean {
        if (accountKey == null || !canWrite(accountKey) || isClosed.get()) return false
        val deferred = CompletableDeferred<Boolean>()
        val (key, seq) =
            synchronized(this) {
                val key = RecordKey(accountKey, questionId)
                val seq = sequence.incrementAndGet()
                val optimistic = transform(currentRecord(key))
                pending.update { it + (key to PendingWrite(seq, optimistic)) }
                publish()
                val sent = commands.trySend(Command.Mutate(key, seq, transform, deferred)).isSuccess
                if (!sent) {
                    releasePending(key, seq)
                    publish()
                    return false
                }
                key to seq
            }
        return try {
            deferred.await()
        } catch (cancelled: CancellationException) {
            releasePending(key, seq)
            publish()
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") error: Exception,
        ) {
            Timber.tag(TimberLogTags.DATABASE).w(error, "Answer-record mutation await failed")
            false
        }
    }

    private suspend fun execute(command: Command) {
        try {
            val success =
                when (command) {
                    is Command.Mutate -> executeMutation(command)
                    is Command.ClearAccount -> executeClearAccount(command)
                }
            command.completion?.complete(success)
        } catch (cancelled: CancellationException) {
            handleCommandCancellation(command)
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            handleCommandFailure(command, error)
        } finally {
            publish()
        }
    }

    private suspend fun executeClearAccount(command: Command.ClearAccount): Boolean =
        try {
            dao.clearAccount(command.accountKey)
            stored.update { map -> map.filterKeys { it.accountKey != command.accountKey } }
            lastCommittedUpdatedAt.keys.filter { it.accountKey == command.accountKey }.forEach {
                lastCommittedUpdatedAt.remove(it)
            }
            true
        } finally {
            clearing.update { map ->
                val left = (map[command.accountKey] ?: 1) - 1
                if (left <= 0) map - command.accountKey else map + (command.accountKey to left)
            }
        }

    private fun handleCommandCancellation(command: Command) {
        when (command) {
            is Command.Mutate -> {
                releasePending(command.key, command.sequence)
                command.completion?.complete(false)
            }
            is Command.ClearAccount -> {
                clearing.update { map ->
                    val left = (map[command.accountKey] ?: 1) - 1
                    if (left <= 0) map - command.accountKey else map + (command.accountKey to left)
                }
                command.completion?.complete(false)
            }
        }
    }

    private fun handleCommandFailure(
        command: Command,
        error: Exception,
    ) {
        // The optimistic overlay is dropped below, so the exposed state falls back to Room's.
        Timber.tag(TimberLogTags.DATABASE).w(error, "Answer-record write failed: %s", command)
        when (command) {
            is Command.Mutate -> {
                releasePending(command.key, command.sequence)
                command.completion?.complete(false)
            }
            is Command.ClearAccount -> {
                command.completion?.complete(false)
            }
        }
    }

    /** Read-modify-write against the stored row: the queued transform never carries a stale copy. */
    private suspend fun executeMutation(command: Command.Mutate): Boolean {
        val key = command.key
        val existing = dao.get(key.accountKey, key.questionId)?.toDomain()
        val next = command.transform(existing)

        when {
            next == null && existing != null -> {
                dao.delete(key.accountKey, key.questionId)
                lastCommittedUpdatedAt[key] = -1L
                stored.update { it - key }
            }
            next != null -> {
                dao.upsert(next.toEntity())
                lastCommittedUpdatedAt[key] = next.updatedAt
                stored.update { it + (key to next) }
            }
        }
        releasePending(key, command.sequence)
        return true
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

        val sortedList = merged.values.sortedByDescending { it.updatedAt }
        val byAccount = sortedList.groupBy { it.accountKey }

        val answeredIds =
            byAccount.mapValues { (_, records) ->
                records
                    .filter { it.answeredAt != null || it.isCorrect != null || it.selectedOption != null }
                    .map { it.questionId }
                    .toSet()
            }
        val viewedExplanationIds =
            byAccount.mapValues { (_, records) ->
                records.filter { it.viewedExplanation }.map { it.questionId }.toSet()
            }
        val viewedHintIds =
            byAccount.mapValues { (_, records) ->
                records.filter { it.viewedHint }.map { it.questionId }.toSet()
            }

        snapshot.value = sortedList
        indexedSnapshot.value =
            RepositorySnapshot(
                list = sortedList,
                byKey = merged,
                byAccount = byAccount,
                answeredIdsByAccount = answeredIds,
                viewedExplanationIdsByAccount = viewedExplanationIds,
                viewedHintIdsByAccount = viewedHintIds,
            )
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

    private data class RepositorySnapshot(
        val list: List<AnswerRecord> = emptyList(),
        val byKey: Map<RecordKey, AnswerRecord> = emptyMap(),
        val byAccount: Map<String, List<AnswerRecord>> = emptyMap(),
        val answeredIdsByAccount: Map<String, Set<Long>> = emptyMap(),
        val viewedExplanationIdsByAccount: Map<String, Set<Long>> = emptyMap(),
        val viewedHintIdsByAccount: Map<String, Set<Long>> = emptyMap(),
    )

    private sealed interface Command {
        val completion: CompletableDeferred<Boolean>?

        data class Mutate(
            val key: RecordKey,
            val sequence: Long,
            val transform: (AnswerRecord?) -> AnswerRecord?,
            override val completion: CompletableDeferred<Boolean>?,
        ) : Command

        data class ClearAccount(
            val accountKey: String,
            override val completion: CompletableDeferred<Boolean>?,
        ) : Command
    }

    private companion object {
        const val MIGRATION_DONE = "answer_records_migrated_v1"
        const val ANSWERED_PREFIX = "answered:"
        const val ANSWER_VIEWED_PREFIX = "answerViewed:"
        const val RETRY_DELAY_MS = 500L
    }
}

private fun AnswerRecordEntity.toDomain() =
    AnswerRecord(
        questionId = questionId,
        accountKey = accountKey,
        title = title,
        categoryLabel = categoryLabel,
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
        categoryLabel = categoryLabel,
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
