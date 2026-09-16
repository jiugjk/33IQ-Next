package com.jiugjk.iq33.feature.feed.data.datasource.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "answer_records",
    primaryKeys = ["accountKey", "questionId"],
)
internal data class AnswerRecordEntity(
    val accountKey: String,
    val questionId: Long,
    val title: String,
    @ColumnInfo(name = "categoryId")
    val categoryLabel: String,
    val selectedOption: String?,
    val isCorrect: Boolean?,
    val correctOption: String? = null,
    val viewedExplanation: Boolean,
    val viewedHint: Boolean,
    /** Cached paid content - see [com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord.hintText]. */
    val hintText: String? = null,
    val explanationText: String? = null,
    val knowledgeDelta: Int?,
    val answeredAt: Long?,
    val updatedAt: Long,
)
