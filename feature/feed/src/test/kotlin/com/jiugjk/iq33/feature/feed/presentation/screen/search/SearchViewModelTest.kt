package com.jiugjk.iq33.feature.feed.presentation.screen.search

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.usecase.SearchQuestionsUseCase
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class SearchViewModelTest {
    private val searchQuestionsUseCase = mockk<SearchQuestionsUseCase>()
    private val sut = SearchViewModel(searchQuestionsUseCase)

    @Test
    fun `the query is part of the state, so the field can be restored from it`() =
        runTest {
            coEvery { searchQuestionsUseCase(any(), any()) } returns Result.Success(listOf(question()))

            sut.onQueryChange("推理")
            advanceUntilIdle()

            sut.uiStateFlow.value.query shouldBeEqualTo "推理"
            sut.uiStateFlow.value.results shouldBeInstanceOf SearchResults.Content::class
        }

    @Test
    fun `a result for a query the user has moved on from is discarded`() =
        runTest {
            val stale = SearchAction.SearchSuccess("旧词", listOf(question()))
            coEvery { searchQuestionsUseCase(any(), any()) } returns Result.Success(emptyList())
            sut.onQueryChange("新词")
            advanceUntilIdle()

            val before = sut.uiStateFlow.value

            stale.reduce(before) shouldBeEqualTo before
        }

    @Test
    fun `clearing the query resets the results without searching for an empty string`() =
        runTest {
            coEvery { searchQuestionsUseCase(any(), any()) } returns Result.Success(listOf(question()))
            sut.onQueryChange("推理")
            advanceUntilIdle()

            sut.onQueryChange("")
            advanceUntilIdle()

            sut.uiStateFlow.value.query shouldBeEqualTo ""
            sut.uiStateFlow.value.results shouldBeInstanceOf SearchResults.Idle::class
        }

    @Test
    fun `re-submitting the query already on screen does not search again`() =
        runTest {
            var calls = 0
            coEvery { searchQuestionsUseCase(any(), any()) } coAnswers {
                calls++
                Result.Success(listOf(question()))
            }

            sut.onQueryChange("推理")
            advanceUntilIdle()
            sut.onQueryChange("推理")
            advanceUntilIdle()

            calls shouldBeEqualTo 1
        }

    private fun question() = QuestionSummary(id = 1, title = "题", tags = emptyList(), upvoteCount = 0, commentCount = 0)
}
