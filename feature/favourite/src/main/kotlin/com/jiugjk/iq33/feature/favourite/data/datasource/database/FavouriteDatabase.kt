package com.jiugjk.iq33.feature.favourite.data.datasource.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SavedQuestionEntity::class], version = 1, exportSchema = false)
internal abstract class FavouriteDatabase : RoomDatabase() {
    abstract fun savedQuestionDao(): SavedQuestionDao
}
