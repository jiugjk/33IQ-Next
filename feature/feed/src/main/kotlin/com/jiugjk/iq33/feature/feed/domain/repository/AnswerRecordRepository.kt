package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import kotlinx.coroutines.flow.Flow

/**
 * Unified local answer-history store. Writes are intentionally narrow so list bind / opening
 * detail / refresh cannot mark progress by accident.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
internal interface AnswerRecordRepository {
    val records: Flow<List<AnswerRecord>>

    fun current(accountKey: String?): List<AnswerRecord>

    fun get(
        accountKey: String?,
        questionId: Long,
    ): AnswerRecord?

    fun answeredIds(accountKey: String?): Set<Long>

    fun viewedExplanationIds(accountKey: String?): Set<Long>

    fun viewedHintIds(accountKey: String?): Set<Long>

    /** ① Submit answer (correct / wrong / already-answered). */
    @Suppress("LongParameterList")
    fun recordAnswer(
        accountKey: String?,
        questionId: Long,
        title: String = "",
        categoryId: String = "",
        selectedOption: String? = null,
        isCorrect: Boolean? = null,
        knowledgeDelta: Int? = null,
        answeredAt: Long = System.currentTimeMillis(),
    )

    /** ② Successful explanation reveal (or server seeanswer). */
    @Suppress("LongParameterList")
    fun recordExplanationViewed(
        accountKey: String?,
        questionId: Long,
        title: String = "",
        categoryId: String = "",
        correctOption: String? = null,
        /** Paid content, cached so re-entering the question never needs another charged request. */
        explanationText: String? = null,
    )

    /** ③ Successful hint reveal. */
    fun recordHintViewed(
        accountKey: String?,
        questionId: Long,
        title: String = "",
        categoryId: String = "",
        /** Paid hint text, cached: 33IQ charges 学识 again for every `showtips` call. */
        hintText: String? = null,
    )

    /**
     * Fills in display metadata (history title / category) for a record that already exists.
     *
     * Deliberately does **not** create one: opening a question is not progress, and a metadata-only
     * record would put every visited question into the history list.
     */
    fun updateMetadata(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryId: String,
    )

    /** Redo: clear answer fields only; keep viewed* and knowledgeDelta. */
    fun clearAnswerState(
        accountKey: String?,
        questionId: Long,
    )

    fun delete(
        accountKey: String?,
        questionId: Long,
    )

    fun clearAll(accountKey: String?)
}
