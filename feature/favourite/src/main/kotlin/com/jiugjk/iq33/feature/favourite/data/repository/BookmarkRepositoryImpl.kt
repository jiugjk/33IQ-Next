package com.jiugjk.iq33.feature.favourite.data.repository

import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionDao
import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionEntity
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG_SEPARATOR = "|||"

internal class BookmarkRepositoryImpl(
    private val savedQuestionDao: SavedQuestionDao,
) : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<SavedQuestion>> =
        savedQuestionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun isBookmarked(id: Long): Boolean = savedQuestionDao.exists(id)

    override suspend fun toggleBookmark(question: SavedQuestion): Boolean =
        if (savedQuestionDao.exists(question.id)) {
            savedQuestionDao.delete(question.id)
            false
        } else {
            savedQuestionDao.insert(question.toEntity())
            true
        }

    private fun SavedQuestionEntity.toDomain() =
        SavedQuestion(
            id = id,
            title = title,
            tags = tags.split(TAG_SEPARATOR).filter { it.isNotBlank() },
            savedAt = savedAt,
        )

    private fun SavedQuestion.toEntity() =
        SavedQuestionEntity(
            id = id,
            title = title,
            tags = tags.joinToString(TAG_SEPARATOR),
            savedAt = savedAt,
        )
}
