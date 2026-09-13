package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.SubmitAnswerUseCase
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.coEvery
import io.mockk.coVerify
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

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class WordBankViewModelTest {
    private val getDetail = mockk<GetQuestionDetailUseCase>()
    private val isBookmarked = mockk<IsBookmarkedUseCase>()
    private val submit = mockk<SubmitAnswerUseCase>()
    private val flow = MutableStateFlow(QuestionProgress(accountKey = "uid:1"))
    private val progressRepo =
        mockk<QuestionProgressRepository> {
            every { current } answers { flow.value }
            every { progress } returns flow
        }
    private val store = ViewModelStore()

    @AfterEach
    fun clear() = store.clear()

    @Test
    fun `typing or tapping a tile never submits and an explicit valid submission sends the selected text once`() =
        runTest {
            val vm = createViewModel()
            vm.load(589_144)
            advanceUntilIdle()
            vm.onEvent(QuestionDetailEvent.DraftAnswerChanged("保"))
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("保"))
            advanceUntilIdle()
            coVerify(exactly = 0) { submit(any(), any()) }
            vm.onEvent(QuestionDetailEvent.CandidateToggled(2))
            advanceUntilIdle()
            coVerify(exactly = 0) { submit(any(), any()) }
            coEvery { submit(589_144, "保") } returns Result.Success(SubmitAnswerResult.AnswerAlreadyViewed)
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("保"))
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("保"))
            advanceUntilIdle()
            coVerify(exactly = 1) { submit(589_144, "保") }
            val content = vm.uiStateFlow.value as QuestionDetailUiState.Content
            content.detail.hasViewedAnswer shouldBeEqualTo true
            content.detail.isAnswered shouldBeEqualTo false
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("保"))
            advanceUntilIdle()
            coVerify(exactly = 1) { submit(any(), any()) }
        }

    @Test
    fun `a locally known restriction blocks submission even before the progress collector catches up`() =
        runTest {
            val vm = createViewModel()
            vm.load(589_144)
            advanceUntilIdle()
            vm.onEvent(QuestionDetailEvent.CandidateToggled(2))
            flow.value = flow.value.copy(viewedAnswerIds = setOf(589_144))
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("保"))
            advanceUntilIdle()
            coVerify(exactly = 0) { submit(any(), any()) }
            (vm.uiStateFlow.value as QuestionDetailUiState.Content).canSelectChoice shouldBeEqualTo false
        }

    private fun createViewModel(): QuestionDetailViewModel {
        val json = requireNotNull(javaClass.getResource("/question-589144-word-bank.json")).readText()
        val detail = requireNotNull(QuestionJsonParser().parseQuestionDetail(json, 589_144))
        coEvery { getDetail(589_144) } returns Result.Success(detail)
        coEvery { isBookmarked(589_144) } returns BookmarkResult.Success(false)
        return QuestionDetailViewModel(
            getDetail,
            isBookmarked,
            mockk(),
            QuestionAnswerUseCases(submit, mockk(), mockk(), mockk()),
            progressRepo,
            mockk(),
        ).also { store.put("detail", it) }
    }
}
