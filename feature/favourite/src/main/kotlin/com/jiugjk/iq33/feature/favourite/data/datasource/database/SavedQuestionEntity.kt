package com.jiugjk.iq33.feature.favourite.data.datasource.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_questions")
internal data class SavedQuestionEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val tags: String,
    val savedAt: Long,
)
