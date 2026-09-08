package com.jiugjk.iq33.feature.favourite.data.datasource.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SavedQuestionDao {
    @Query("SELECT * FROM saved_questions ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedQuestionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_questions WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedQuestionEntity)

    @Query("DELETE FROM saved_questions WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Flips the bookmark for [entity] and returns its new state.
     *
     * Room runs a `@Transaction` method's whole body in one transaction, so the read and the write
     * cannot interleave with a second toggle - two concurrent taps can no longer both read "not
     * bookmarked" and both insert.
     */
    @Transaction
    suspend fun toggle(entity: SavedQuestionEntity): Boolean =
        if (exists(entity.id)) {
            delete(entity.id)
            false
        } else {
            insert(entity)
            true
        }
}
