package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.IOException

class AnswerRepositoryTest {
    private val remote = mockk<AnswerRemoteDataSource>()
    private val progress =
        mockk<QuestionProgressRepository>(relaxed = true) {
            every { current } returns QuestionProgress(accountKey = "uid:1")
        }
    private val sut = AnswerRepositoryImpl(remote, progress)

    @Test
    fun `correct wrong and repeated answers are persisted`() =
        runTest {
            val results =
                listOf(SubmitAnswerResult.Correct(null, null), SubmitAnswerResult.Wrong(null, null), SubmitAnswerResult.AlreadyAnswered)
            results.forEachIndexed { index, result ->
                val id = index.toLong() + 1
                coEvery { remote.submitAnswer(id, "A") } returns result
                sut.submitAnswer(id, "A") shouldBeEqualTo Result.Success(result)
                verify(exactly = 1) { progress.recordAnswered(id, "uid:1") }
            }
        }

    @Test
    fun `seeanswer persists a separate restriction without inventing an answered record`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.AnswerAlreadyViewed
            sut.submitAnswer(1, "A") shouldBeEqualTo Result.Success(SubmitAnswerResult.AnswerAlreadyViewed)
            verify(exactly = 1) { progress.recordAnswerViewed(1, "uid:1") }
            verify(exactly = 0) { progress.recordAnswered(any(), any()) }
        }

    @Test
    fun `limit and failed requests never mark a question answered`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.LimitReached
            coEvery { remote.submitAnswer(2, "A") } throws IOException("offline")
            sut.submitAnswer(1, "A") shouldBeEqualTo Result.Success(SubmitAnswerResult.LimitReached)
            sut.submitAnswer(2, "A") shouldBeInstanceOf Result.Failure::class
            verify(exactly = 0) { progress.recordAnswered(any(), any()) }
        }

    @Test
    fun `a result is associated with the initiating account not the newly active account`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } coAnswers {
                every { progress.current } returns QuestionProgress(accountKey = "uid:2")
                SubmitAnswerResult.AlreadyAnswered
            }
            sut.submitAnswer(1, "A")
            verify(exactly = 1) { progress.recordAnswered(1, "uid:1") }
        }
}
