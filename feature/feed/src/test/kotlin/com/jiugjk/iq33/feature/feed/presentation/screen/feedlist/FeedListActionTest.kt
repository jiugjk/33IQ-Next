package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.CategorySource
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class FeedListActionTest {
    @Test
    fun `a page that belongs to a category the user left is discarded`() {
        val stateAfterSwitch =
            FeedListUiState.Content(selectedCategory = CATEGORY_B, questions = listOf(question(10)), page = 1)

        val reduced = FeedListAction.LoadMoreSuccess(CATEGORY_A, page = 2, newQuestions = listOf(question(20))).reduce(stateAfterSwitch)

        reduced shouldBeEqualTo stateAfterSwitch
    }

    @Test
    fun `a page that no longer follows the current one is discarded`() {
        val refreshedState = FeedListUiState.Content(selectedCategory = CATEGORY_A, questions = listOf(question(10)), page = 1)

        val reduced = FeedListAction.LoadMoreSuccess(CATEGORY_A, page = 4, newQuestions = listOf(question(40))).reduce(refreshedState)

        reduced shouldBeEqualTo refreshedState
    }

    @Test
    fun `the matching next page is appended`() {
        val state = FeedListUiState.Content(selectedCategory = CATEGORY_A, questions = listOf(question(10)), page = 1)

        val reduced =
            FeedListAction.LoadMoreSuccess(CATEGORY_A, page = 2, newQuestions = listOf(question(20))).reduce(state)
                as FeedListUiState.Content

        reduced.questions.map { it.id } shouldBeEqualTo listOf(10L, 20L)
        reduced.page shouldBeEqualTo 2
    }

    @Test
    fun `a failed page stays retryable instead of being treated as the end of the list`() {
        val state = FeedListUiState.Content(selectedCategory = CATEGORY_A, questions = listOf(question(10)), isLoadingMore = true)

        val reduced = FeedListAction.LoadMoreFailure(CATEGORY_A).reduce(state) as FeedListUiState.Content

        reduced.canLoadMore shouldBeEqualTo true
        reduced.loadMoreFailed shouldBeEqualTo true
        reduced.isLoadingMore shouldBeEqualTo false
    }

    @Test
    fun `an empty next page does mean the end of the list`() {
        val state = FeedListUiState.Content(selectedCategory = CATEGORY_A, questions = listOf(question(10)), page = 1)

        val reduced =
            FeedListAction.LoadMoreSuccess(CATEGORY_A, page = 2, newQuestions = emptyList()).reduce(state) as FeedListUiState.Content

        reduced.canLoadMore shouldBeEqualTo false
    }

    @Test
    fun `a failed first load keeps the category so it can be retried`() {
        val reduced = FeedListAction.LoadFailure(CATEGORY_B).reduce(FeedListUiState.Loading(CATEGORY_B))

        reduced.selectedCategory shouldBeEqualTo CATEGORY_B
    }

    @Test
    fun `refreshing keeps the listed questions until the new page arrives`() {
        val state = FeedListUiState.Content(selectedCategory = CATEGORY_A, questions = listOf(question(10)), page = 2)

        val reduced = FeedListAction.RefreshStart(CATEGORY_A).reduce(state) as FeedListUiState.Content

        reduced.isRefreshing shouldBeEqualTo true
        reduced.questions.map { it.id } shouldBeEqualTo listOf(10L)
    }

    @Test
    fun `a failed refresh does not blank the list`() {
        val state =
            FeedListUiState.Content(
                selectedCategory = CATEGORY_A,
                questions = listOf(question(10)),
                isRefreshing = true,
            )

        val reduced = FeedListAction.RefreshFailure(CATEGORY_A).reduce(state) as FeedListUiState.Content

        reduced.isRefreshing shouldBeEqualTo false
        reduced.questions.map { it.id } shouldBeEqualTo listOf(10L)
    }

    private fun question(id: Long) = QuestionSummary(id = id, title = "题 $id", tags = emptyList(), upvoteCount = 0, commentCount = 0)

    private companion object {
        val CATEGORY_A = Category("detective", "侦探推理", CategorySource.Tag("侦探推理"))
        val CATEGORY_B = Category("logic", "逻辑思维", CategorySource.Tag("逻辑思维"))
    }
}
