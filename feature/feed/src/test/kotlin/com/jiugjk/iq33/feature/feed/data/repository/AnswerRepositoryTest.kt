package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerFeedbackPreferences
import com.jiugjk.iq33.library.network.KnowledgeChangeLog
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
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
            every { setHintRevealPending(any(), any(), any()) } returns true
        }
    private val answerRecords =
        mockk<AnswerRecordRepository>(relaxed = true) {
            coEvery { recordExplanationViewedAwait(any(), any(), any(), any(), any(), any()) } returns true
            coEvery { recordHintViewedAwait(any(), any(), any(), any(), any()) } returns true
        }
    private val feedback = mockk<AnswerFeedbackPreferences>(relaxed = true)
    private val session = MutableStateFlow(IqSession(status = SessionStatus.AUTHENTICATED, score = "100", accountKey = "uid:1"))
    private val sessionManager =
        mockk<SessionManager>(relaxed = true) {
            every { sessionFlow } returns session
            every { sessionEpoch } returns 7
        }
    private val knowledgeLog = mockk<KnowledgeChangeLog>(relaxed = true)
    private val sut = AnswerRepositoryImpl(remote, progress, answerRecords, feedback, sessionManager, knowledgeLog)

    @Test
    fun `correct wrong and repeated answers are persisted`() =
        runTest {
            val results =
                listOf(SubmitAnswerResult.Correct(null, null), SubmitAnswerResult.Wrong(null, null), SubmitAnswerResult.AlreadyAnswered)
            results.forEachIndexed { index, result ->
                val id = index.toLong() + 1
                coEvery { remote.submitAnswer(id, "A") } returns result
                sut.submitAnswer(id, "A") shouldBeEqualTo Result.Success(result)
                verify(exactly = 1) {
                    answerRecords.recordAnswer(
                        accountKey = "uid:1",
                        questionId = id,
                        title = any(),
                        categoryLabel = any(),
                        selectedOption = any(),
                        isCorrect = any(),
                        knowledgeDelta = any(),
                        answeredAt = any(),
                    )
                }
            }
        }

    @Test
    fun `seeanswer persists a separate restriction without inventing an answered record`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.AnswerAlreadyViewed
            sut.submitAnswer(1, "A") shouldBeEqualTo Result.Success(SubmitAnswerResult.AnswerAlreadyViewed)
            verify(exactly = 1) { answerRecords.recordExplanationViewed("uid:1", 1) }
            verify(exactly = 0) {
                answerRecords.recordAnswer(
                    accountKey = any(),
                    questionId = any(),
                    title = any(),
                    categoryLabel = any(),
                    selectedOption = any(),
                    isCorrect = any(),
                    knowledgeDelta = any(),
                    answeredAt = any(),
                )
            }
        }

    @Test
    fun `limit and failed requests never mark a question answered`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.LimitReached
            coEvery { remote.submitAnswer(2, "A") } throws IOException("offline")
            sut.submitAnswer(1, "A") shouldBeEqualTo Result.Success(SubmitAnswerResult.LimitReached)
            sut.submitAnswer(2, "A") shouldBeInstanceOf Result.Failure::class
            verify(exactly = 0) {
                answerRecords.recordAnswer(
                    accountKey = any(),
                    questionId = any(),
                    title = any(),
                    categoryLabel = any(),
                    selectedOption = any(),
                    isCorrect = any(),
                    knowledgeDelta = any(),
                    answeredAt = any(),
                )
            }
        }

    @Test
    fun `a result is associated with the initiating account not the newly active account`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } coAnswers {
                every { progress.current } returns QuestionProgress(accountKey = "uid:2")
                SubmitAnswerResult.AlreadyAnswered
            }
            sut.submitAnswer(1, "A")
            verify(exactly = 1) {
                answerRecords.recordAnswer(
                    accountKey = "uid:1",
                    questionId = 1,
                    title = any(),
                    categoryLabel = any(),
                    selectedOption = any(),
                    isCorrect = any(),
                    knowledgeDelta = any(),
                    answeredAt = any(),
                )
            }
        }

    @Test
    fun `a reply that lands after an account switch updates nothing that belongs to the new account`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } coAnswers {
                // The user switches accounts while the answer is in flight: new owner, new generation.
                every { progress.current } returns QuestionProgress(accountKey = "uid:2")
                session.value = IqSession(status = SessionStatus.AUTHENTICATED, score = "500", accountKey = "uid:2")
                every { sessionManager.sessionEpoch } returns 8
                SubmitAnswerResult.Correct(scoreDelta = 3, myScore = 42)
            }

            sut.submitAnswer(1, "A")

            // The record is still attributed to the account that asked; the store refuses it.
            verify(exactly = 1) {
                answerRecords.recordAnswer(
                    accountKey = "uid:1",
                    questionId = 1,
                    title = any(),
                    categoryLabel = any(),
                    selectedOption = any(),
                    isCorrect = any(),
                    knowledgeDelta = any(),
                    answeredAt = any(),
                )
            }
            // Streak and change log belong to an account too - neither is moved for the new one.
            verify(exactly = 0) { feedback.recordCorrect(any()) }
            verify(exactly = 0) { knowledgeLog.append(any(), any(), any(), any(), any()) }
            // The score write carries the owner and generation it was captured under, so the session
            // manager refuses it inside the same lock that commits state.
            verify(exactly = 1) { sessionManager.applyServerScore(42, "uid:1", 7) }
            verify(exactly = 0) { sessionManager.applyServerScore(any<Int>(), "uid:2", any()) }
        }

    @Test
    fun `an uninterrupted answer updates the streak, the log and the score of its own account`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.Correct(scoreDelta = 3, myScore = 42)

            sut.submitAnswer(1, "A")

            verify(exactly = 1) { feedback.recordCorrect("uid:1") }
            verify(exactly = 1) { knowledgeLog.append("uid:1", 1, 3, any(), any()) }
            verify(exactly = 1) { sessionManager.applyServerScore(42, "uid:1", 7) }
        }

    @Test
    fun `a wrong answer resets the streak of its own account only`() =
        runTest {
            coEvery { remote.submitAnswer(1, "A") } returns SubmitAnswerResult.Wrong(scoreDelta = -1, myScore = null)

            sut.submitAnswer(1, "A")

            verify(exactly = 1) { feedback.recordWrong("uid:1") }
            verify(exactly = 1) { sessionManager.applyOptimisticDelta(-1, "uid:1", 7) }
        }

    @Test
    fun `hint reveal records viewedHint only`() =
        runTest {
            coEvery { remote.revealHint(9) } returns
                com.jiugjk.iq33.feature.feed.domain.model
                    .HintReveal("tip")
            sut.revealHint(9)
            coVerify(exactly = 1) {
                answerRecords.recordHintViewedAwait(
                    accountKey = "uid:1",
                    questionId = 9,
                    title = any(),
                    categoryLabel = any(),
                    hintText = "tip",
                )
            }
        }
}
