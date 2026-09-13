package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.coEvery
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
class FeedListViewModelTest {
    private val getList = mockk<GetQuestionListUseCase>()
    private val progress = MemoryProgress()
    private val models = ViewModelStore()
    private var modelCount = 0
    private var requestCount = 0

    @AfterEach
    fun clearModels() = models.clear()

    @Test
    fun `pull refresh resets cursor, excludes the current screen, and cold reopen uses the new position`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, THIRD)
            coEvery { getList(Category.ALL, THIRD) } returns page(3, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(1L)
            // Refresh starts from page 1 again but skips the on-screen id, so it walks to NEXT.
            sut.onEvent(FeedListEvent.Refreshed)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(2L)
            content(sut).batchRevision shouldBeEqualTo 2

            val reopened = createViewModel()
            reopened.onInit()
            advanceUntilIdle()
            content(reopened).questions.map { it.id } shouldBeEqualTo listOf(3L)
        }

    @Test
    fun `failed refresh keeps content but clears the persisted cursor`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returnsMany listOf(page(1, NEXT), Result.Failure())
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            sut.onEvent(FeedListEvent.Refreshed)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(1L)
            content(sut).refreshFailed shouldBeEqualTo true
            progress.feedPosition(Category.ALL.id) shouldBeEqualTo FeedPosition()
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, null)
            sut.onEvent(FeedListEvent.Refreshed)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(2L)
            content(sut).refreshFailed shouldBeEqualTo false
        }

    @Test
    fun `duplicate pages are skipped but repeated cursors cannot loop forever`() =
        runTest {
            progress.saveFeedPosition(Category.ALL.id, FeedPosition(NEXT, setOf(1)), "uid:1")
            coEvery { getList(Category.ALL, NEXT) } returns page(1, THIRD)
            coEvery { getList(Category.ALL, THIRD) } returns page(1, NEXT)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).questions shouldBeEqualTo emptyList()
            content(sut).noNewContent shouldBeEqualTo true
            content(sut).canLoadMore shouldBeEqualTo false
        }

    @Test
    fun `duplicate scan is bounded to five requests and refresh restarts from the first page`() =
        runTest {
            progress.saveFeedPosition(Category.ALL.id, FeedPosition("cursor0", setOf(1)), "uid:1")
            var requests = 0
            coEvery { getList(any(), any()) } coAnswers {
                requests++
                page(1, "cursor$requests")
            }
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            requests shouldBeEqualTo 5
            progress.feedPosition(Category.ALL.id).nextPageUrl shouldBeEqualTo "cursor5"
            requests = 0
            coEvery { getList(Category.ALL, null) } returns page(2, null)
            sut.onEvent(FeedListEvent.Refreshed)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(2L)
        }

    @Test
    fun `filter skips an answered-only page but switching it off restores those cards`() =
        runTest {
            progress.markAnswered(1)
            progress.setHideAnswered(true)
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(2L)
            sut.onEvent(FeedListEvent.HideAnsweredChanged(false))
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(1L, 2L)
            progress.markAnswered(2)
            advanceUntilIdle()
            content(sut).progress.answeredIds shouldBeEqualTo setOf(1L, 2L)
        }

    @Test
    fun `load more is deduplicated and failure remains retryable`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns Result.Failure()
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            sut.onEvent(FeedListEvent.EndReached)
            advanceUntilIdle()
            content(sut).loadMoreFailed shouldBeEqualTo true
            progress.feedPosition(Category.ALL.id).nextPageUrl shouldBeEqualTo NEXT
            coEvery { getList(Category.ALL, NEXT) } returns Result.Success(QuestionPage(listOf(question(1), question(2)), null))
            sut.onEvent(FeedListEvent.LoadMoreRetried)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(1L, 2L)
            content(sut).canLoadMore shouldBeEqualTo false
        }

    @Test
    fun `switching category cancels an in flight request`() =
        runTest {
            val other = Category.DEFAULT_CATEGORIES.first { it != Category.ALL }
            coEvery { getList(Category.ALL, null) } coAnswers { awaitCancellation() }
            coEvery { getList(other, null) } returns page(2, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            sut.onEvent(FeedListEvent.CategorySelected(other))
            advanceUntilIdle()
            content(sut).selectedCategory shouldBeEqualTo other
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(2L)
            progress.feedPosition(Category.ALL.id) shouldBeEqualTo FeedPosition()
        }

    @Test
    fun `a brief detail round trip keeps the batch but an hours old foreground refreshes`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            sut.onForeground()
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(1L)
            sut.onForeground(System.currentTimeMillis() + 2 * 60 * 60 * 1000L)
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(2L)
        }

    @Test
    fun `account switch discards an in flight batch and loads the new accounts position`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } coAnswers { awaitCancellation() }
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            sut.onEvent(FeedListEvent.EndReached)
            advanceUntilIdle()
            coEvery { getList(Category.ALL, null) } returns page(3, null)
            progress.progress.value = QuestionProgress(accountKey = "uid:2")
            advanceUntilIdle()
            content(sut).questions.map { it.id } shouldBeEqualTo listOf(3L)
            content(sut).progress.accountKey shouldBeEqualTo "uid:2"
            progress.progress.value = QuestionProgress(accountKey = "uid:1")
            progress.feedPosition(Category.ALL.id).nextPageUrl shouldBeEqualTo NEXT
        }

    @Test
    fun `viewed analysis records update the list and are included in the hide filter`() =
        runTest {
            coEvery { getList(Category.ALL, null) } returns page(1, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            progress.markViewed(1)
            advanceUntilIdle()
            content(sut).progress.viewedAnswerIds shouldBeEqualTo setOf(1L)
            content(sut).progress.answeredIds shouldBeEqualTo emptySet()
            sut.onEvent(FeedListEvent.HideAnsweredChanged(true))
            advanceUntilIdle()
            content(sut).visibleQuestions shouldBeEqualTo emptyList()
            sut.onEvent(FeedListEvent.HideAnsweredChanged(false))
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(1L)
        }

    @Test
    fun `auto-continues load more when the filter hides the whole batch`() =
        runTest {
            progress.setHideAnswered(true)
            progress.markAnswered(1)
            progress.markAnswered(2)
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, THIRD)
            coEvery { getList(Category.ALL, THIRD) } returns page(3, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            // First batch is all hidden; ViewModel should keep walking without a manual EndReached.
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(3L)
        }

    @Test
    fun `after a full hidden batch with a next cursor, auto loadMore finds a visible card`() =
        runTest {
            progress.setHideAnswered(true)
            // Five hidden-only pages fill MAX_BATCH_REQUESTS with no visible card, leaving a next cursor.
            progress.markAnswered(1)
            progress.markAnswered(2)
            progress.markAnswered(3)
            progress.markAnswered(4)
            progress.markAnswered(5)
            var requests = 0
            coEvery { getList(any(), any()) } coAnswers {
                requests++
                val id = requests.toLong()
                if (requests <= 5) {
                    page(id, "cursor$requests")
                } else {
                    // loadMore continuation finally returns a visible question
                    Result.Success(
                        com.jiugjk.iq33.feature.feed.domain.model.QuestionPage(
                            listOf(question(99)),
                            null,
                        ),
                    )
                }
            }
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(99L)
            content(sut).canLoadMore shouldBeEqualTo false
            // Initial walk (5) + at least one auto loadMore
            (requests >= 6) shouldBeEqualTo true
        }

    @Test
    fun `load more also walks filtered pages instead of stopping on a hidden-only reply`() =
        runTest {
            progress.setHideAnswered(true)
            progress.markAnswered(2)
            coEvery { getList(Category.ALL, null) } returns page(1, NEXT)
            coEvery { getList(Category.ALL, NEXT) } returns page(2, THIRD)
            coEvery { getList(Category.ALL, THIRD) } returns page(3, null)
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(1L)
            sut.onEvent(FeedListEvent.EndReached)
            advanceUntilIdle()
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(1L, 3L)
        }

    @Test
    fun `a page cycle longer than one batch ends the automatic walk instead of circling`() =
        runTest {
            progress.setHideAnswered(true)
            // Six questions whose next-page links form a ring: A-B-C-D-E-F-A. All of them are hidden,
            // so no batch ever produces a visible card. A per-batch cursor set only sees five of the
            // six hops, which is what let this walk go on forever.
            val ring = listOf(null, "cursor1", "cursor2", "cursor3", "cursor4", "cursor5")
            (1..6).forEach { progress.markAnswered(it.toLong()) }
            ring.indices.forEach { index ->
                coEvery { getList(Category.ALL, ring[index]) } answers {
                    requestCount++
                    page(index.toLong() + 1, ring[(index + 1) % ring.size] ?: "cursor0")
                }
            }
            coEvery { getList(Category.ALL, "cursor0") } answers {
                requestCount++
                page(1, "cursor1")
            }

            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()

            // One lap plus the repeated cursor that proves the ring - and then it stops.
            (requestCount <= ring.size + 1) shouldBeEqualTo true
            content(sut).questions.map { it.id }.toSet() shouldBeEqualTo setOf(1L, 2L, 3L, 4L, 5L, 6L)
            content(sut).visibleQuestions shouldBeEqualTo emptyList()
            content(sut).nextPageUrl shouldBeEqualTo null
            content(sut).canLoadMore shouldBeEqualTo false

            val settled = requestCount
            advanceUntilIdle()
            requestCount shouldBeEqualTo settled
        }

    @Test
    fun `an endlessly filtered feed stops on the chain budget and offers to continue`() =
        runTest {
            progress.setHideAnswered(true)
            // Every page has a brand-new cursor, so no cycle is ever detected; only the chain budget
            // can end this. Each question is already answered, so nothing ever becomes visible.
            (1..MAX_CHAIN_REQUESTS * 2).forEach { progress.markAnswered(it.toLong()) }
            coEvery { getList(Category.ALL, any()) } answers {
                requestCount++
                page(requestCount.toLong(), "cursor$requestCount")
            }

            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()

            requestCount shouldBeEqualTo MAX_CHAIN_REQUESTS
            val paused = content(sut)
            paused.autoPagingPaused shouldBeEqualTo true
            paused.loadMoreFailed shouldBeEqualTo false
            paused.canContinuePaging shouldBeEqualTo true

            // It stays stopped until the user says otherwise.
            advanceUntilIdle()
            requestCount shouldBeEqualTo MAX_CHAIN_REQUESTS

            coEvery { getList(Category.ALL, any()) } answers {
                requestCount++
                Result.Success(QuestionPage(listOf(question(9_999)), null))
            }
            sut.onEvent(FeedListEvent.ContinuePagingRequested)
            advanceUntilIdle()

            (requestCount > MAX_CHAIN_REQUESTS) shouldBeEqualTo true
            content(sut).visibleQuestions.map { it.id } shouldBeEqualTo listOf(9_999L)
            content(sut).autoPagingPaused shouldBeEqualTo false
        }

    @Test
    fun `a refresh gives the walk a fresh budget`() =
        runTest {
            progress.setHideAnswered(true)
            (1..MAX_CHAIN_REQUESTS * 2).forEach { progress.markAnswered(it.toLong()) }
            coEvery { getList(Category.ALL, any()) } answers {
                requestCount++
                page(requestCount.toLong(), "cursor$requestCount")
            }
            val sut = createViewModel()
            sut.onInit()
            advanceUntilIdle()
            content(sut).autoPagingPaused shouldBeEqualTo true

            sut.onEvent(FeedListEvent.Refreshed)
            advanceUntilIdle()

            (requestCount > MAX_CHAIN_REQUESTS) shouldBeEqualTo true
        }

    private fun createViewModel() = FeedListViewModel(getList, progress).also { models.put("vm${modelCount++}", it) }

    private fun content(vm: FeedListViewModel) = vm.uiStateFlow.value as FeedListUiState.Content

    private fun question(id: Long) = QuestionSummary(id, "题 $id", emptyList(), 0, 0)

    private fun page(
        id: Long,
        next: String?,
    ) = Result.Success(QuestionPage(listOf(question(id)), next))

    private class MemoryProgress : QuestionProgressRepository {
        override val progress = MutableStateFlow(QuestionProgress(accountKey = "uid:1"))
        override val current get() = progress.value
        private val positions = mutableMapOf<Pair<String?, String>, FeedPosition>()

        fun markAnswered(questionId: Long) {
            progress.value = current.copy(answeredIds = current.answeredIds + questionId)
        }

        fun markViewed(questionId: Long) {
            progress.value = current.copy(viewedAnswerIds = current.viewedAnswerIds + questionId)
        }

        override fun setAnswerRevealPending(
            questionId: Long,
            pending: Boolean,
            accountKey: String?,
        ): Boolean {
            if (current.accountKey != accountKey) return false
            val ids = current.pendingAnswerRevealIds
            progress.value = current.copy(pendingAnswerRevealIds = if (pending) ids + questionId else ids - questionId)
            return true
        }

        override fun setHideAnswered(hide: Boolean) {
            progress.value = current.copy(hideAnswered = hide)
        }

        override fun feedPosition(categoryId: String) = positions[current.accountKey to categoryId] ?: FeedPosition()

        override fun saveFeedPosition(
            categoryId: String,
            position: FeedPosition,
            accountKey: String?,
        ) {
            if (current.accountKey == accountKey) positions[accountKey to categoryId] = position
        }

        override fun clearFeedPosition(
            categoryId: String,
            accountKey: String?,
        ) {
            if (current.accountKey == accountKey) positions.remove(accountKey to categoryId)
        }
    }

    private companion object {
        const val NEXT = "https://www.33iq.com/question/?cursor=next"
        const val THIRD = "https://www.33iq.com/question/?cursor=third"
    }
}
