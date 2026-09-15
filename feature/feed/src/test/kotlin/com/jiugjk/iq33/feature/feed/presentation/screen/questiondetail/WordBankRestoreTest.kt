package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.data.repository.InMemoryAnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.SubmitAnswerUseCase
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Word-bank questions on re-entry.
 *
 * A stored answer is text; the picker works in tile indices. Restoring one from the other is what
 * keeps an answered word-bank question from showing a "submitted" state above an empty field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class WordBankRestoreTest {
    private val getDetail = mockk<GetQuestionDetailUseCase>()
    private val isBookmarked = mockk<IsBookmarkedUseCase>()
    private val submit = mockk<SubmitAnswerUseCase>()
    private val records = InMemoryAnswerRecordRepository()
    private val flow = MutableStateFlow(QuestionProgress(accountKey = ACCOUNT))
    private val progressRepo =
        mockk<QuestionProgressRepository>(relaxed = true) {
            every { current } answers { flow.value }
            every { progress } returns flow
        }
    private val store = ViewModelStore()

    @AfterEach
    fun clear() = store.clear()

    @Test
    fun `duplicate tiles are matched by position, so the stored answer is shown and selected`() =
        runTest {
            // 保 appears twice in this question's tiles; the restored selection must be one of them.
            records.seed(stored("保"))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val content = content(vm)
            content.selectedCandidateIndices shouldBeEqualTo listOf(2)
            content.wordBankAnswer shouldBeEqualTo "保"
            content.wordBankDisplayAnswer shouldBeEqualTo "保"
            content.submission shouldBeEqualTo
                SubmissionState.Done(
                    submittedAnswer = "保",
                    result = SubmitAnswerResult.Correct(null, null),
                )
        }

    @Test
    fun `an answer that no longer matches the tiles is still echoed instead of disappearing`() =
        runTest {
            records.seed(stored("龍"))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val content = content(vm)
            content.selectedCandidateIndices shouldBeEqualTo emptyList()
            content.answerEcho shouldBeEqualTo "龍"
            content.wordBankDisplayAnswer shouldBeEqualTo "龍"
        }

    @Test
    fun `tiles are matched by position and length, never by first occurrence of the text`() {
        matchCandidateIndices(listOf("天", "天", "下"), "天天下") shouldBeEqualTo listOf(0, 1, 2)
        matchCandidateIndices(listOf("天", "天", "下"), "天下") shouldBeEqualTo listOf(0, 2)
        // A longer tile wins over a shorter one that is its prefix.
        matchCandidateIndices(listOf("天", "天下"), "天下") shouldBeEqualTo listOf(1)
        // Each tile is used at most once.
        matchCandidateIndices(listOf("天", "下"), "天天") shouldBeEqualTo null
        matchCandidateIndices(listOf("天", "下"), "地") shouldBeEqualTo null
        matchCandidateIndices(emptyList(), "天") shouldBeEqualTo null
        matchCandidateIndices(listOf("天"), "") shouldBeEqualTo null
    }

    private fun content(vm: QuestionDetailViewModel) = vm.uiStateFlow.value as QuestionDetailUiState.Content

    private fun stored(answer: String) =
        AnswerRecord(
            questionId = QUESTION_ID,
            accountKey = ACCOUNT,
            selectedOption = answer,
            isCorrect = true,
            answeredAt = 1,
            updatedAt = 1,
        )

    private fun createViewModel(): QuestionDetailViewModel {
        val json = requireNotNull(javaClass.getResource("/question-589144-word-bank.json")).readText()
        val detail = requireNotNull(QuestionJsonParser().parseQuestionDetail(json, QUESTION_ID))
        coEvery { getDetail(QUESTION_ID) } returns Result.Success(detail)
        coEvery { isBookmarked(QUESTION_ID) } returns BookmarkResult.Success(false)
        return QuestionDetailViewModel(
            getDetail,
            isBookmarked,
            mockk(),
            QuestionAnswerUseCases(submit, mockk(), mockk(), mockk()),
            progressRepo,
            mockk(),
            records,
            mockk(relaxed = true),
        ).also { store.put("detail", it) }
    }

    private companion object {
        const val ACCOUNT = "uid:1"
        const val QUESTION_ID = 589_144L
    }
}
