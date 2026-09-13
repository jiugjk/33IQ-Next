package com.jiugjk.iq33.feature.feed.data.datasource.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AnswerRecordEntity::class], version = 2, exportSchema = false)
internal abstract class AnswerRecordDatabase : RoomDatabase() {
    abstract fun answerRecordDao(): AnswerRecordDao
}
