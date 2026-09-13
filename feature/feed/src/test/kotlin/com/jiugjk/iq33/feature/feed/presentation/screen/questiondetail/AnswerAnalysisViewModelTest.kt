package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.AnswerRevealUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteHintUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RecoverAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RevealAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.SubmitAnswerUseCase
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class AnswerAnalysisViewModelTest {
    private val getDetail = mockk<GetQuestionDetailUseCase>()
    private val bookmark = mockk<IsBookmarkedUseCase>()
    private val quote = mockk<QuoteAnswerUseCase>()
    private val reveal = mockk<RevealAnswerUseCase>()
    private val recover = mockk<RecoverAnswerUseCase>()
    private val submit = mockk<SubmitAnswerUseCase>()
    private val hintQuote = mockk<QuoteHintUseCase>()
    private val flow = MutableStateFlow(QuestionProgress(accountKey = "uid:1"))
    private val progressRepo =
        mockk<QuestionProgressRepository> {
            every { current } answers { flow.value }
            every { progress } returns flow
        }
    private val store = ViewModelStore()
    private val cost = AnswerQuote(1, 60, true)
    private val content = AnswerReveal("A", "解析")

    @AfterEach
    fun clear() = store.clear()

    @Test
    fun `opening or cancelling a quote never purchases or fetches the answer`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            coEvery { quote(1) } returns Result.Success(cost)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            (state(vm).answerReveal as RevealState.QuoteReady).quote.cost shouldBeEqualTo 60
            vm.onEvent(QuestionDetailEvent.AnswerFlowDismissed)
            advanceUntilIdle()
            state(vm).answerReveal shouldBeEqualTo RevealState.Idle
            state(vm).canSelectChoice shouldBeEqualTo true
            coVerify(exactly = 0) { reveal(any()) }
            coVerify(exactly = 0) { recover(any()) }
        }

    @Test
    fun `even a free quote requires confirmation and a double tap reveals only once`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            val free = AnswerQuote(1, 0, false)
            coEvery { quote(1) } returns Result.Success(free)
            coEvery { reveal(free) } returns Result.Success(content)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            coVerify(exactly = 0) { reveal(any()) }
            vm.onEvent(QuestionDetailEvent.AnswerRevealConfirmed)
            vm.onEvent(QuestionDetailEvent.AnswerRevealConfirmed)
            advanceUntilIdle()
            coVerify(exactly = 1) { reveal(free) }
            (state(vm).answerReveal as RevealState.Revealed).reveal shouldBeEqualTo content
            state(vm).detail.hasViewedAnswer shouldBeEqualTo true
            state(vm).detail.isAnswered shouldBeEqualTo false
            state(vm).canSelectChoice shouldBeEqualTo false
            vm.load(1)
            state(vm).answerReveal shouldBeEqualTo RevealState.Revealed(content)
        }

    @Test
    fun `an open analysis confirmation blocks competing submissions and hint purchases`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            coEvery { quote(1) } returns Result.Success(cost)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("A"))
            vm.onEvent(QuestionDetailEvent.HintQuoteRequested)
            advanceUntilIdle()
            coVerify(exactly = 0) { submit(any(), any()) }
            coVerify(exactly = 0) { hintQuote(any()) }
        }

    @Test
    fun `pending status after reload exposes recovery only and no new quote or purchase`() =
        runTest {
            flow.value = flow.value.copy(pendingAnswerRevealIds = setOf(1))
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            state(vm).canStartAnswerReveal shouldBeEqualTo false
            state(vm).canRecoverAnswerReveal shouldBeEqualTo true
            state(vm).canSelectChoice shouldBeEqualTo false
            coEvery { recover(1) } returns Result.Success(content)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            vm.onEvent(QuestionDetailEvent.AnswerRevealRecovered)
            advanceUntilIdle()
            coVerify(exactly = 0) { quote(any()) }
            coVerify(exactly = 0) { reveal(any()) }
            coVerify(exactly = 1) { recover(1) }
        }

    @Test
    fun `an uncertain failure cannot be dismissed into a new purchase`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            coEvery { quote(1) } returns Result.Success(cost)
            coEvery { reveal(cost) } returns Result.Failure(afterSideEffect = true)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            vm.onEvent(QuestionDetailEvent.AnswerRevealConfirmed)
            advanceUntilIdle()
            vm.onEvent(QuestionDetailEvent.AnswerFlowDismissed)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            state(vm).canSelectChoice shouldBeEqualTo false
            state(vm).canRecoverAnswerReveal shouldBeEqualTo true
            coVerify(exactly = 1) { quote(any()) }
            coVerify(exactly = 1) { reveal(any()) }
        }

    @Test
    fun `changing question abandons an in flight quote`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            coEvery { quote(1) } coAnswers { awaitCancellation() }
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            vm.load(2)
            advanceUntilIdle()
            state(vm).detail.id shouldBeEqualTo 2
            state(vm).answerReveal shouldBeEqualTo RevealState.Idle
        }

    @Test
    fun `switching accounts invalidates the previous accounts confirmation`() =
        runTest {
            val vm = create()
            vm.load(1)
            advanceUntilIdle()
            coEvery { quote(1) } returns Result.Success(cost)
            vm.onEvent(QuestionDetailEvent.AnswerQuoteRequested)
            advanceUntilIdle()
            flow.value = QuestionProgress(accountKey = "uid:2")
            vm.onEvent(QuestionDetailEvent.AnswerRevealConfirmed)
            advanceUntilIdle()
            coVerify(exactly = 0) { reveal(any()) }
            state(vm).answerReveal shouldBeEqualTo RevealState.Idle
        }

    private fun state(vm: QuestionDetailViewModel) = vm.uiStateFlow.value as QuestionDetailUiState.Content

    private fun create(): QuestionDetailViewModel {
        coEvery { getDetail(any()) } coAnswers {
            val id = firstArg<Long>()
            Result.Success(
                QuestionJsonParser()
                    .parseQuestionDetail("""[{"qc_context":"题"}]""", id)!!
                    .copy(isAnswerRevealPending = id in flow.value.pendingAnswerRevealIds),
            )
        }
        coEvery { bookmark(any()) } returns BookmarkResult.Success(false)
        return QuestionDetailViewModel(
            getDetail,
            bookmark,
            mockk(),
            QuestionAnswerUseCases(submit, hintQuote, mockk(), mockk()),
            progressRepo,
            AnswerRevealUseCases(quote, reveal, recover),
        ).also { store.put("detail", it) }
    }
}
