package com.jiugjk.iq33.feature.favourite.data.repository

import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionDao
import com.jiugjk.iq33.feature.favourite.data.datasource.database.SavedQuestionEntity
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkRepositoryImplTest {
    private val savedQuestionDao = mockk<SavedQuestionDao>()
    private val sut = BookmarkRepositoryImpl(savedQuestionDao)

    @Test
    fun `a database failure is reported rather than thrown into the caller's coroutine`() =
        runTest {
            coEvery { savedQuestionDao.exists(any()) } throws IOException("database is corrupt")

            sut.isBookmarked(1) shouldBeInstanceOf BookmarkResult.Failure::class
        }

    @Test
    fun `a failing observation emits a failure instead of cancelling the collector`() =
        runTest {
            every { savedQuestionDao.observeAll() } returns flow { throw IOException("cannot open database") }

            sut.observeBookmarks().first() shouldBeInstanceOf BookmarkResult.Failure::class
        }

    @Test
    fun `toggling goes through the dao's transaction rather than a separate read and write`() =
        runTest {
            coEvery { savedQuestionDao.toggle(any()) } returns true

            sut.toggleBookmark(savedQuestion()) shouldBeEqualTo BookmarkResult.Success(true)

            coVerify(exactly = 1) { savedQuestionDao.toggle(any<SavedQuestionEntity>()) }
            coVerify(exactly = 0) { savedQuestionDao.exists(any()) }
        }

    @Test
    fun `removing is a delete, so removing twice cannot re-add the bookmark`() =
        runTest {
            coEvery { savedQuestionDao.delete(any()) } returns Unit

            sut.removeBookmark(1)
            sut.removeBookmark(1)

            coVerify(exactly = 2) { savedQuestionDao.delete(1) }
            coVerify(exactly = 0) { savedQuestionDao.insert(any()) }
        }

    @Test
    fun `stored tags round-trip back into the domain model`() =
        runTest {
            every { savedQuestionDao.observeAll() } returns
                flowOf(listOf(SavedQuestionEntity(id = 1, title = "题", tags = "逻辑思维|||趣味益智", savedAt = 5L)))

            val result = sut.observeBookmarks().first() as BookmarkResult.Success

            result.value.single().tags shouldBeEqualTo listOf("逻辑思维", "趣味益智")
        }

    private fun savedQuestion() = SavedQuestion(id = 1, title = "题", tags = listOf("逻辑思维"), savedAt = 5L)
}
