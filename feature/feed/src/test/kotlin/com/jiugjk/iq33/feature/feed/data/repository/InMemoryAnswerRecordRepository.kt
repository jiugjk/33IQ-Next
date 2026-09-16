package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Test double mirroring [AnswerRecordRepositoryImpl] write semantics without Room. */
internal class InMemoryAnswerRecordRepository(
    private val activeAccountKey: (() -> String?)? = null,
) : AnswerRecordRepository {
    private val snapshot = MutableStateFlow<List<AnswerRecord>>(emptyList())

    override val records: Flow<List<AnswerRecord>> = snapshot

    override fun current(accountKey: String?): List<AnswerRecord> {
        if (accountKey == null) return emptyList()
        return snapshot.value.filter { it.accountKey == accountKey }
    }

    override fun get(
        accountKey: String?,
        questionId: Long,
    ): AnswerRecord? {
        if (accountKey == null) return null
        return snapshot.value.firstOrNull { it.accountKey == accountKey && it.questionId == questionId }
    }

    override fun answeredIds(accountKey: String?): Set<Long> =
        current(accountKey)
            .filter { it.answeredAt != null || it.isCorrect != null || it.selectedOption != null }
            .map { it.questionId }
            .toSet()

    override fun viewedExplanationIds(accountKey: String?): Set<Long> =
        current(accountKey).filter { it.viewedExplanation }.map { it.questionId }.toSet()

    override fun viewedHintIds(accountKey: String?): Set<Long> = current(accountKey).filter { it.viewedHint }.map { it.questionId }.toSet()

    override fun recordAnswer(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        selectedOption: String?,
        isCorrect: Boolean?,
        knowledgeDelta: Int?,
        answeredAt: Long,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        val existing = get(accountKey, questionId)
        upsert(
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
                selectedOption = selectedOption ?: existing?.selectedOption,
                isCorrect = isCorrect ?: existing?.isCorrect,
                correctOption = if (isCorrect == true) selectedOption ?: existing?.correctOption else existing?.correctOption,
                knowledgeDelta = knowledgeDelta ?: existing?.knowledgeDelta,
                answeredAt = answeredAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    @Suppress("LongParameterList")
    override fun recordExplanationViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        correctOption: String?,
        explanationText: String?,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        val existing = get(accountKey, questionId)
        upsert(
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
                viewedExplanation = true,
                correctOption = correctOption?.takeIf { it.isNotBlank() } ?: existing?.correctOption,
                explanationText = explanationText?.takeIf { it.isNotBlank() } ?: existing?.explanationText,
                answeredAt = existing?.answeredAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    @Suppress("LongParameterList")
    override suspend fun recordExplanationViewedAwait(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        correctOption: String?,
        explanationText: String?,
    ): Boolean {
        recordExplanationViewed(accountKey, questionId, title, categoryLabel, correctOption, explanationText)
        return accountKey != null && canWrite(accountKey)
    }

    override fun recordHintViewed(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        hintText: String?,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        val existing = get(accountKey, questionId)
        upsert(
            (existing ?: AnswerRecord(questionId = questionId, accountKey = accountKey)).copy(
                title = title.ifBlank { existing?.title.orEmpty() },
                categoryLabel = categoryLabel.ifBlank { existing?.categoryLabel.orEmpty() },
                viewedHint = true,
                hintText = hintText?.takeIf { it.isNotBlank() } ?: existing?.hintText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun recordHintViewedAwait(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
        hintText: String?,
    ): Boolean {
        recordHintViewed(accountKey, questionId, title, categoryLabel, hintText)
        return accountKey != null && canWrite(accountKey)
    }

    override fun updateMetadata(
        accountKey: String?,
        questionId: Long,
        title: String,
        categoryLabel: String,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        val existing = get(accountKey, questionId) ?: return
        upsert(
            existing.copy(
                title = title.ifBlank { existing.title },
                categoryLabel = categoryLabel.ifBlank { existing.categoryLabel },
            ),
        )
    }

    override fun delete(
        accountKey: String?,
        questionId: Long,
    ) {
        if (accountKey == null || !canWrite(accountKey)) return
        snapshot.update { list -> list.filterNot { it.accountKey == accountKey && it.questionId == questionId } }
    }

    override fun clearAll(accountKey: String?) {
        if (accountKey == null || !canWrite(accountKey)) return
        snapshot.update { list -> list.filterNot { it.accountKey == accountKey } }
    }

    /** Seed migrated-style rows for tests that previously wrote prefs sets. */
    fun seed(record: AnswerRecord) = upsert(record)

    private fun canWrite(accountKey: String?): Boolean {
        if (accountKey == null) return false
        val active = activeAccountKey ?: return true
        return accountKey == active()
    }

    private fun upsert(record: AnswerRecord) {
        snapshot.update { list ->
            list.filterNot { it.accountKey == record.accountKey && it.questionId == record.questionId } + record
        }
    }
}
