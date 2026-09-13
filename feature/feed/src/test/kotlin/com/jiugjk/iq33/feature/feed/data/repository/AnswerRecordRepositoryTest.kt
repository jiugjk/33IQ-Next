package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordEntity
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

/**
 * The orderings the repository has to survive.
 *
 * These drive the real [AnswerRecordRepositoryImpl] against a controllable DAO and scheduler - the
 * writes are really queued, really interleaved and really replayed - rather than asserting on a
 * hand-built state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnswerRecordRepositoryTest {
    private val account = "uid:1"
    private val sessionManager =
        mockk<SessionManager> {
            every { sessionFlow } returns MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1"))
        }

    @Test
    fun `a later viewed-explanation write does not lose the answer that is still being written`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val gate = CompletableDeferred<Unit>()
            // The first write is held open, so the second command is queued behind an unfinished one -
            // exactly the window in which a snapshot captured up front would go stale.
            dao.beforeWrite = { if (it == "upsert:1" && !gate.isCompleted) gate.await() }
            val repository = repository(dao)

            repository.recordAnswer(account, 1, selectedOption = "A", isCorrect = true, knowledgeDelta = 3)
            repository.recordExplanationViewed(account, 1, explanationText = "because")
            gate.complete(Unit)
            advanceUntilIdle()

            val stored = requireNotNull(dao.stored(account, 1))
            stored.isCorrect shouldBeEqualTo true
            stored.selectedOption shouldBeEqualTo "A"
            stored.knowledgeDelta shouldBeEqualTo 3
            stored.viewedExplanation shouldBeEqualTo true
            stored.explanationText shouldBeEqualTo "because"
        }

    @Test
    fun `a delete queued behind an answer write is not undone by that write`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val gate = CompletableDeferred<Unit>()
            dao.beforeWrite = { if (it == "upsert:1" && !gate.isCompleted) gate.await() }
            val repository = repository(dao)

            repository.recordAnswer(account, 1, selectedOption = "A", isCorrect = false)
            repository.delete(account, 1)
            // The queued answer write is released only now; it must not resurrect the deleted row.
            gate.complete(Unit)
            advanceUntilIdle()

            dao.writeLog shouldBeEqualTo listOf("upsert:1", "delete:1")
            dao.stored(account, 1).shouldBeNull()
            repository.get(account, 1).shouldBeNull()
            repository.current(account) shouldBeEqualTo emptyList()
        }

    @Test
    fun `an outdated database notification cannot drop writes that are still queued`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val gate = CompletableDeferred<Unit>()
            dao.beforeWrite = { if (!gate.isCompleted) gate.await() }
            val repository = repository(dao)

            repository.recordAnswer(account, 1, selectedOption = "A", isCorrect = true)
            repository.recordHintViewed(account, 2, hintText = "tip")
            advanceUntilIdle()

            // Room replays a query that was taken before either write landed.
            dao.emitStale(listOf(entity(questionId = 3)))
            advanceUntilIdle()

            repository.get(account, 1)?.isCorrect shouldBeEqualTo true
            repository.get(account, 2)?.viewedHint shouldBeEqualTo true

            gate.complete(Unit)
            advanceUntilIdle()

            repository.get(account, 1)?.selectedOption shouldBeEqualTo "A"
            repository.get(account, 2)?.hintText shouldBeEqualTo "tip"
            dao.storedRows().map { it.questionId }.sorted() shouldBeEqualTo listOf(1L, 2L)
        }

    @Test
    fun `a cleared account stays cleared while a stale snapshot is replayed`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val repository = repository(dao)
            repository.recordAnswer(account, 1, selectedOption = "A", isCorrect = true)
            advanceUntilIdle()
            val before = dao.storedRows()

            val gate = CompletableDeferred<Unit>()
            dao.beforeWrite = { if (!gate.isCompleted) gate.await() }
            repository.clearAll(account)
            dao.emitStale(before)
            advanceUntilIdle()

            repository.current(account) shouldBeEqualTo emptyList()

            gate.complete(Unit)
            advanceUntilIdle()

            repository.current(account) shouldBeEqualTo emptyList()
            dao.storedRows() shouldBeEqualTo emptyList()
        }

    @Test
    fun `a failed write falls back to what the database holds instead of showing a phantom record`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            dao.failWrite = { it.questionId == 1L }
            val repository = repository(dao)

            repository.recordAnswer(account, 1, selectedOption = "A", isCorrect = true)
            advanceUntilIdle()

            repository.get(account, 1).shouldBeNull()

            // The worker survived the failure: the next write still goes through.
            dao.failWrite = { false }
            repository.recordAnswer(account, 2, selectedOption = "B", isCorrect = false)
            advanceUntilIdle()

            repository.get(account, 2)?.isCorrect shouldBeEqualTo false
        }

    @Test
    fun `records survive a rebuild of the repository over the same database`() =
        runTest {
            val rows = linkedMapOf<Pair<String, Long>, AnswerRecordEntity>()
            val first = repository(FakeAnswerRecordDao(rows))
            first.recordAnswer(account, 1, title = "T", categoryId = "C", selectedOption = "A", isCorrect = true)
            first.recordHintViewed(account, 1, hintText = "tip")
            advanceUntilIdle()

            // Same rows, new repository: what a process restart sees.
            val restarted = repository(FakeAnswerRecordDao(rows))
            advanceUntilIdle()

            val record = requireNotNull(restarted.get(account, 1))
            record.isCorrect shouldBeEqualTo true
            record.title shouldBeEqualTo "T"
            record.hintText shouldBeEqualTo "tip"
            restarted.answeredIds(account) shouldBeEqualTo setOf(1L)
        }

    @Test
    fun `metadata is filled in on an existing record and never creates one`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val repository = repository(dao)

            repository.updateMetadata(account, 7, title = "no record yet", categoryId = "逻辑思维")
            advanceUntilIdle()
            repository.get(account, 7).shouldBeNull()

            repository.recordAnswer(account, 7, selectedOption = "A", isCorrect = true)
            repository.updateMetadata(account, 7, title = "第七题", categoryId = "逻辑思维")
            advanceUntilIdle()

            val stored = requireNotNull(dao.stored(account, 7))
            stored.title shouldBeEqualTo "第七题"
            stored.categoryId shouldBeEqualTo "逻辑思维"
        }

    @Test
    fun `writes for another account are refused`() =
        runTest {
            val dao = FakeAnswerRecordDao()
            val repository = repository(dao)

            repository.recordAnswer("uid:2", 1, selectedOption = "A", isCorrect = true)
            advanceUntilIdle()

            dao.storedRows() shouldBeEqualTo emptyList()
        }

    private fun TestScope.repository(dao: FakeAnswerRecordDao) =
        AnswerRecordRepositoryImpl(
            dao = dao,
            preferences = FakeSharedPreferences(),
            sessionManager = sessionManager,
            ioScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

    private fun entity(questionId: Long) =
        AnswerRecordEntity(
            accountKey = account,
            questionId = questionId,
            title = "",
            categoryId = "",
            selectedOption = null,
            isCorrect = null,
            viewedExplanation = false,
            viewedHint = false,
            knowledgeDelta = null,
            answeredAt = null,
            updatedAt = 1,
        )
}
