package com.jiugjk.iq33.feature.feed.presentation.screen.history

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.feed.data.repository.InMemoryAnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * History filtering.
 *
 * The distinction under test is "this account has no history" versus "the current condition matched
 * nothing": only the first is an empty screen. The second has to keep the search field and the
 * filter chips, because they are the only way back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class HistoryViewModelTest {
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
    fun `a filter that matches nothing keeps the controls that can undo it`() =
        runTest {
            records.seed(record(1, isCorrect = true, title = "答对的题"))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onFilter(HistoryFilter.WRONG)

            val state = vm.uiStateFlow.value
            state shouldBeInstanceOf HistoryUiState.Content::class
            val content = state as HistoryUiState.Content
            content.records shouldBeEqualTo emptyList()
            content.isZeroMatch shouldBeEqualTo true
            content.filter shouldBeEqualTo HistoryFilter.WRONG

            vm.onFilter(HistoryFilter.ALL)
            (vm.uiStateFlow.value as HistoryUiState.Content).records.map { it.questionId } shouldBeEqualTo listOf(1L)
        }

    @Test
    fun `a keyword that matches nothing can be cleared without leaving the screen`() =
        runTest {
            records.seed(record(1, isCorrect = true, title = "答对的题"))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onQuery("完全不存在的关键词")

            val content = vm.uiStateFlow.value as HistoryUiState.Content
            content.isZeroMatch shouldBeEqualTo true
            content.query shouldBeEqualTo "完全不存在的关键词"

            vm.onResetFilters()

            val restored = vm.uiStateFlow.value as HistoryUiState.Content
            restored.query shouldBeEqualTo ""
            restored.filter shouldBeEqualTo HistoryFilter.ALL
            restored.records.map { it.questionId } shouldBeEqualTo listOf(1L)
        }

    @Test
    fun `an account without any history is the only empty state`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiStateFlow.value shouldBeEqualTo HistoryUiState.Empty
        }

    @Test
    fun `search matches title, category and question number`() =
        runTest {
            records.seed(record(101, isCorrect = true, title = "谁是凶手", categoryId = "侦探推理"))
            records.seed(record(202, isCorrect = false, title = "", categoryId = ""))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onQuery("凶手")
            matched(vm) shouldBeEqualTo listOf(101L)

            vm.onQuery("侦探")
            matched(vm) shouldBeEqualTo listOf(101L)

            // A record migrated without any metadata is still findable by its question number.
            vm.onQuery("202")
            matched(vm) shouldBeEqualTo listOf(202L)
        }

    private fun matched(vm: HistoryViewModel) = (vm.uiStateFlow.value as HistoryUiState.Content).records.map { it.questionId }

    private fun createViewModel() = HistoryViewModel(records, progressRepo).also { store.put("history", it) }

    private fun record(
        questionId: Long,
        isCorrect: Boolean?,
        title: String = "",
        categoryId: String = "",
    ) = AnswerRecord(
        questionId = questionId,
        accountKey = ACCOUNT,
        title = title,
        categoryId = categoryId,
        selectedOption = "A",
        isCorrect = isCorrect,
        answeredAt = 1,
        updatedAt = questionId,
    )

    private companion object {
        const val ACCOUNT = "uid:1"
    }
}
