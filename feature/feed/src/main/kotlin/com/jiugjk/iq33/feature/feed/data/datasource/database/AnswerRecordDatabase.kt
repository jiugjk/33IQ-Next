package com.jiugjk.iq33.feature.feed.data.datasource.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema version that added the local cache of paid hint / explanation text. */
internal const val VERSION_WITH_TEXT_CACHE = 3
private const val VERSION_BEFORE_TEXT_CACHE = 2
private const val VERSION_INITIAL = 1

@Database(entities = [AnswerRecordEntity::class], version = VERSION_WITH_TEXT_CACHE, exportSchema = false)
internal abstract class AnswerRecordDatabase : RoomDatabase() {
    abstract fun answerRecordDao(): AnswerRecordDao
}

/**
 * Adds the remembered correct option.
 *
 * Nullable with no default, matching [AnswerRecordEntity.correctOption]: "not known yet" and "known
 * to be X" have to stay distinguishable, and a `DEFAULT` would make every pre-existing row claim an
 * answer it never recorded.
 */
internal val MIGRATION_1_2 =
    object : Migration(VERSION_INITIAL, VERSION_BEFORE_TEXT_CACHE) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE answer_records ADD COLUMN correctOption TEXT")
        }
    }

/**
 * Adds the local cache of already-paid-for hint / explanation text.
 *
 * Written as a real migration rather than left to the destructive fallback: dropping the table here
 * would delete the answer history this release is about.
 */
internal val MIGRATION_2_3 =
    object : Migration(VERSION_BEFORE_TEXT_CACHE, VERSION_WITH_TEXT_CACHE) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE answer_records ADD COLUMN hintText TEXT")
            db.execSQL("ALTER TABLE answer_records ADD COLUMN explanationText TEXT")
        }
    }
