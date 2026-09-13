package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordDao
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

/**
 * In-memory stand-in for the Room DAO with the same observable behaviour.
 *
 * Room is not available off-device, so this keeps the parts the repository actually depends on: a
 * queryable table, a `Flow` that replays the whole table, and suspending writes. The hooks let a
 * test stall a write, fail one, or replay a stale query result - the orderings the repository has to
 * survive.
 */
internal class FakeAnswerRecordDao(
    /** Rows are kept outside the DAO so a "process restart" can rebuild a new DAO over them. */
    private val rows: MutableMap<Pair<String, Long>, AnswerRecordEntity> = linkedMapOf(),
) : AnswerRecordDao {
    private val emissions = MutableStateFlow<List<AnswerRecordEntity>>(rows.values.toList())

    /** Called before every write; a test can suspend here to hold the writer in place. */
    var beforeWrite: (suspend (String) -> Unit)? = null

    /** Entities matching this fail their write, as a database error would. */
    var failWrite: (AnswerRecordEntity) -> Boolean = { false }

    val writeLog = mutableListOf<String>()

    override fun observeAll(): Flow<List<AnswerRecordEntity>> = emissions

    override suspend fun getAll(): List<AnswerRecordEntity> = rows.values.toList()

    override suspend fun get(
        accountKey: String,
        questionId: Long,
    ): AnswerRecordEntity? = rows[accountKey to questionId]

    override suspend fun upsert(entity: AnswerRecordEntity) {
        beforeWrite?.invoke("upsert:${entity.questionId}")
        if (failWrite(entity)) throw IOException("write failed for ${entity.questionId}")
        writeLog += "upsert:${entity.questionId}"
        rows[entity.accountKey to entity.questionId] = entity
        publish()
    }

    override suspend fun upsertAll(entities: List<AnswerRecordEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun delete(
        accountKey: String,
        questionId: Long,
    ) {
        beforeWrite?.invoke("delete:$questionId")
        writeLog += "delete:$questionId"
        rows.remove(accountKey to questionId)
        publish()
    }

    override suspend fun clearAccount(accountKey: String) {
        beforeWrite?.invoke("clear:$accountKey")
        writeLog += "clear:$accountKey"
        rows.keys.filter { it.first == accountKey }.forEach(rows::remove)
        publish()
    }

    /** Replays an outdated query result, as Room's invalidation tracker can while writes are queued. */
    fun emitStale(snapshot: List<AnswerRecordEntity>) {
        emissions.value = snapshot
    }

    fun stored(
        accountKey: String,
        questionId: Long,
    ): AnswerRecordEntity? = rows[accountKey to questionId]

    fun storedRows(): List<AnswerRecordEntity> = rows.values.toList()

    private fun publish() {
        emissions.value = rows.values.sortedByDescending { it.updatedAt }
    }
}
