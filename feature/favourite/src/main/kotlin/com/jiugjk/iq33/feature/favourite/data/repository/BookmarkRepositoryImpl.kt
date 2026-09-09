package com.jiugjk.iq33.feature.favourite.data.repository

import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionDao
import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionEntity
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

private const val TAG_SEPARATOR = "|||"

internal class BookmarkRepositoryImpl(
    private val savedQuestionDao: SavedQuestionDao,
) : BookmarkRepository {
    override fun observeBookmarks(): Flow<BookmarkResult<List<SavedQuestion>>> =
        savedQuestionDao
            .observeAll()
            .map { entities -> BookmarkResult.Success(entities.map { it.toDomain() }) as BookmarkResult<List<SavedQuestion>> }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable

                Timber.tag(TimberLogTags.DATABASE).w(throwable, "Failed to observe bookmarks")
                emit(BookmarkResult.Failure(throwable))
            }

    override suspend fun isBookmarked(id: Long): BookmarkResult<Boolean> =
        storageResult("Failed to read bookmark state for $id") { savedQuestionDao.exists(id) }

    override suspend fun toggleBookmark(question: SavedQuestion): BookmarkResult<Boolean> =
        storageResult("Failed to toggle bookmark for ${question.id}") { savedQuestionDao.toggle(question.toEntity()) }

    override suspend fun removeBookmark(id: Long): BookmarkResult<Unit> =
        storageResult("Failed to remove bookmark $id") { savedQuestionDao.delete(id) }

    private suspend fun <T> storageResult(
        failureMessage: String,
        block: suspend () -> T,
    ): BookmarkResult<T> =
        runCatching { block() }
            .fold(
                onSuccess = { value -> BookmarkResult.Success(value) },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable

                    Timber.tag(TimberLogTags.DATABASE).w(throwable, failureMessage)
                    BookmarkResult.Failure(throwable)
                },
            )

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
