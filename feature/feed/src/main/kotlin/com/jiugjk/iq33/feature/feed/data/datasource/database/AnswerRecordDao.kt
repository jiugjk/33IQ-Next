package com.jiugjk.iq33.feature.feed.data.datasource.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AnswerRecordDao {
    @Query("SELECT * FROM answer_records ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnswerRecordEntity>>

    @Query("SELECT * FROM answer_records")
    suspend fun getAll(): List<AnswerRecordEntity>

    @Query("SELECT * FROM answer_records WHERE accountKey = :accountKey AND questionId = :questionId LIMIT 1")
    suspend fun get(
        accountKey: String,
        questionId: Long,
    ): AnswerRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnswerRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AnswerRecordEntity>)

    @Query("DELETE FROM answer_records WHERE accountKey = :accountKey AND questionId = :questionId")
    suspend fun delete(
        accountKey: String,
        questionId: Long,
    )

    @Query("DELETE FROM answer_records WHERE accountKey = :accountKey")
    suspend fun clearAccount(accountKey: String)
}
