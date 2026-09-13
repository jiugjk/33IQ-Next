package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class QuestionRepositoryTest {
    private val remote = mockk<QuestionRemoteDataSource>()
    private val progress = mockk<QuestionProgressRepository>()
    private val sut = QuestionRepositoryImpl(remote, progress)
    private val detail = QuestionJsonParser().parseQuestionDetail("""[{"qc_id":"42","qc_context":"题目"}]""", 42)!!

    @Test
    fun `detail includes the answered flag before any submission`() =
        runTest {
            every { progress.current } returns QuestionProgress(accountKey = "uid:1", answeredIds = setOf(42))
            coEvery { remote.fetchQuestionDetail(42) } returns detail
            val loaded = sut.getQuestionDetail(42) as Result.Success
            loaded.value.isAnswered shouldBeEqualTo true
        }

    @Test
    fun `a saved analysis restriction is visible when reentering the detail`() =
        runTest {
            every { progress.current } returns QuestionProgress(accountKey = "uid:1", viewedAnswerIds = setOf(42))
            coEvery { remote.fetchQuestionDetail(42) } returns detail
            val loaded = (sut.getQuestionDetail(42) as Result.Success).value
            loaded.hasViewedAnswer shouldBeEqualTo true
            loaded.isAnswered shouldBeEqualTo false
            loaded.isSubmissionBlocked shouldBeEqualTo true
        }

    @Test
    fun `an absent local record is not claimed to be answered`() =
        runTest {
            every { progress.current } returns QuestionProgress(accountKey = "uid:1")
            coEvery { remote.fetchQuestionDetail(42) } returns detail
            val loaded = sut.getQuestionDetail(42) as Result.Success
            loaded.value.isAnswered shouldBeEqualTo false
        }

    @Test
    fun `an in flight detail never merges a different accounts progress`() =
        runTest {
            every { progress.current } returns QuestionProgress(accountKey = "uid:1")
            coEvery { remote.fetchQuestionDetail(42) } coAnswers {
                every { progress.current } returns QuestionProgress(accountKey = "uid:2", answeredIds = setOf(42))
                detail
            }
            val loaded = sut.getQuestionDetail(42) as Result.Success
            loaded.value.isAnswered shouldBeEqualTo false
        }
}
