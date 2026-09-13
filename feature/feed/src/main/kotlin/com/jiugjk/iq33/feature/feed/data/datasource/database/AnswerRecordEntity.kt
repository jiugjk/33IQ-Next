package com.jiugjk.iq33.feature.feed.data.datasource.database

import androidx.room.Entity

@Entity(
    tableName = "answer_records",
    primaryKeys = ["accountKey", "questionId"],
)
internal data class AnswerRecordEntity(
    val accountKey: String,
    val questionId: Long,
    val title: String,
    val categoryId: String,
    val selectedOption: String?,
    val isCorrect: Boolean?,
    val viewedExplanation: Boolean,
    val viewedHint: Boolean,
    val knowledgeDelta: Int?,
    val answeredAt: Long?,
    val updatedAt: Long,
)
