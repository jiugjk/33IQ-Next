package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.ViewModelStore
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkResult
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.feed.data.repository.InMemoryAnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.AnswerRevealUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteHintUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RecoverAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RevealAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RevealHintUseCase
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
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Re-entering a question this account already paid for.
 *
 * Covers both directions of the bug: content that *is* cached must come back without any charged
 * call, and an entitlement whose content is *not* cached must not be presented as loaded - it has to
 * leave a way to get the text back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class QuestionDetailRestoreTest {
    private val getDetail = mockk<GetQuestionDetailUseCase>()
    private val isBookmarked = mockk<IsBookmarkedUseCase>()
    private val submit = mockk<SubmitAnswerUseCase>()
    private val quoteHint = mockk<QuoteHintUseCase>()
    private val revealHint = mockk<RevealHintUseCase>()
    private val quoteAnswer = mockk<QuoteAnswerUseCase>()
    private val revealAnswer = mockk<RevealAnswerUseCase>()
    private val recoverAnswer = mockk<RecoverAnswerUseCase>()
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
    fun `cached analysis comes back on re-entry without any charged call`() =
        runTest {
            records.seed(
                record().copy(
                    viewedExplanation = true,
                    correctOption = "B",
                    explanationText = "因为 B",
                ),
            )
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val content = content(vm)
            val revealed = content.answerReveal as RevealState.Revealed
            revealed.reveal shouldBeEqualTo AnswerReveal(answerText = "B", explanationText = "因为 B")
            content.canStartAnswerReveal shouldBeEqualTo false
            content.canRecoverAnswerReveal shouldBeEqualTo false
            coVerify(exactly = 0) { quoteAnswer(any()) }
            coVerify(exactly = 0) { revealAnswer(any()) }
            coVerify(exactly = 0) { recoverAnswer(any()) }
        }

    @Test
    fun `an entitlement without cached text offers a fetch-only recovery instead of an empty reveal`() =
        runTest {
            // A record written before the text cache existed: viewed, but nothing to show.
            records.seed(record().copy(viewedExplanation = true, correctOption = "B"))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val restored = content(vm)
            restored.answerReveal shouldBeInstanceOf RevealState.Entitled::class
            restored.isAnswerRevealEntitled shouldBeEqualTo true
            // The paid entry point stays closed; only the free re-read is offered.
            restored.canStartAnswerReveal shouldBeEqualTo false
            restored.canRecoverAnswerReveal shouldBeEqualTo true
            // The known answer still marks the right choice, without faking a revealed analysis.
            restored.revealedCorrectOption shouldBeEqualTo "B"

            coEvery { recoverAnswer(QUESTION_ID) } returns Result.Success(AnswerReveal("B", "解析正文"))
            vm.onEvent(QuestionDetailEvent.AnswerRevealRecovered)
            advanceUntilIdle()

            (content(vm).answerReveal as RevealState.Revealed).reveal.explanationText shouldBeEqualTo "解析正文"
            coVerify(exactly = 1) { recoverAnswer(QUESTION_ID) }
            coVerify(exactly = 0) { quoteAnswer(any()) }
            coVerify(exactly = 0) { revealAnswer(any()) }
        }

    @Test
    fun `a cached hint is restored and never re-bought`() =
        runTest {
            records.seed(record().copy(viewedHint = true, hintText = "提示正文"))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val content = content(vm)
            (content.hintReveal as RevealState.Revealed).reveal shouldBeEqualTo HintReveal("提示正文")
            content.canStartHintReveal shouldBeEqualTo false
            coVerify(exactly = 0) { quoteHint(any()) }
            coVerify(exactly = 0) { revealHint(any()) }
        }

    @Test
    fun `a hint entitlement without text is reported as such and buys nothing on its own`() =
        runTest {
            records.seed(record().copy(viewedHint = true))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            val content = content(vm)
            content.hintReveal shouldBeInstanceOf RevealState.Entitled::class
            content.isHintRevealEntitled shouldBeEqualTo true
            // showtips charges every time, so re-reading stays an explicit, priced decision.
            content.canStartHintReveal shouldBeEqualTo true
            coVerify(exactly = 0) { quoteHint(any()) }
            coVerify(exactly = 0) { revealHint(any()) }
        }

    @Test
    fun `a restored submission carries no feedback token while a live one does`() =
        runTest {
            records.seed(record().copy(selectedOption = "A", isCorrect = true, knowledgeDelta = 2, answeredAt = 1))
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            // History echo: the screen must not replay haptics, animation or the 学识 float for it.
            (content(vm).submission as SubmissionState.Done).completionToken shouldBeEqualTo null
        }

    @Test
    fun `a live submission carries a feedback token`() =
        runTest {
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()

            coEvery { submit(QUESTION_ID, "A", any()) } returns Result.Success(SubmitAnswerResult.Correct(2, 20))
            vm.onEvent(QuestionDetailEvent.ChoiceSelected("A"))
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("A"))
            advanceUntilIdle()

            val live = content(vm).submission as SubmissionState.Done
            (live.completionToken != null) shouldBeEqualTo true
        }

    @Test
    fun `answering fills in the history title and category the search needs`() =
        runTest {
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()
            // The answer repository writes the record from inside the submission, with whatever title
            // it was handed - exactly as in production, where no backfill has run yet at that point.
            val submittedTitles = mutableListOf<String>()
            coEvery { submit(QUESTION_ID, "A", any()) } coAnswers {
                submittedTitles += thirdArg<String>()
                records.recordAnswer(
                    accountKey = ACCOUNT,
                    questionId = QUESTION_ID,
                    title = thirdArg<String>(),
                    selectedOption = "A",
                    isCorrect = true,
                )
                Result.Success(SubmitAnswerResult.Correct(2, 20))
            }

            vm.onEvent(QuestionDetailEvent.ChoiceSelected("A"))
            vm.onEvent(QuestionDetailEvent.AnswerSubmitted("A"))
            advanceUntilIdle()

            // The first-answer path is the one that used to store a blank title: the 学识 change log is
            // appended inside the submission, long before the screen reports the question's metadata.
            submittedTitles shouldBeEqualTo listOf("谁是凶手？")
            val record = requireNotNull(records.get(ACCOUNT, QUESTION_ID))
            record.title shouldBeEqualTo "谁是凶手？"
            record.categoryId shouldBeEqualTo "侦探推理"
        }

    @Test
    fun `opening a question backfills metadata on an old record but never creates one`() =
        runTest {
            val vm = createViewModel()
            vm.load(QUESTION_ID)
            advanceUntilIdle()
            // No record yet: visiting a question is not progress.
            records.current(ACCOUNT) shouldBeEqualTo emptyList()

            records.seed(record().copy(answeredAt = 1, selectedOption = "A", isCorrect = false))
            vm.load(QUESTION_ID, forceReload = true)
            advanceUntilIdle()

            val record = requireNotNull(records.get(ACCOUNT, QUESTION_ID))
            record.title shouldBeEqualTo "谁是凶手？"
            record.categoryId shouldBeEqualTo "侦探推理"
        }

    private fun content(vm: QuestionDetailViewModel) = vm.uiStateFlow.value as QuestionDetailUiState.Content

    private fun record() = AnswerRecord(questionId = QUESTION_ID, accountKey = ACCOUNT, updatedAt = 1)

    private fun createViewModel(): QuestionDetailViewModel {
        coEvery { getDetail(QUESTION_ID) } returns Result.Success(detail())
        coEvery { isBookmarked(QUESTION_ID) } returns BookmarkResult.Success(false)
        coEvery { submit(any(), any(), any()) } returns Result.Success(SubmitAnswerResult.AlreadyAnswered)
        return QuestionDetailViewModel(
            getDetail,
            isBookmarked,
            mockk(),
            QuestionAnswerUseCases(submit, quoteHint, revealHint, mockk()),
            progressRepo,
            AnswerRevealUseCases(quoteAnswer, revealAnswer, recoverAnswer),
            records,
            mockk(relaxed = true),
        ).also { store.put("detail", it) }
    }

    private fun detail() =
        QuestionDetail(
            id = QUESTION_ID,
            title = "谁是凶手？",
            bodyText = "题面",
            imageUrls = emptyList(),
            tags = listOf("推理"),
            breadcrumb = listOf("首页", "侦探推理"),
            author = null,
            publishedDate = null,
            upvoteCount = 0,
            isUpvoted = false,
            commentCount = 0,
            collectCount = 0,
            rightRatio = null,
            questionType = QuestionType.CHOICE,
            choices = listOf(Choice("A", "甲"), Choice("B", "乙")),
            analysis = null,
            sourceUrl = "https://www.33iq.com/question/$QUESTION_ID.html",
        )

    private companion object {
        const val ACCOUNT = "uid:1"
        const val QUESTION_ID = 42L
    }
}
