package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRevealRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.datasource.remote.IqResponseException
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRevealRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Quote, confirm, pay once, then fetch.
 *
 * The durable "pending" latch is written **before** any request that the server could charge for, so
 * a timeout, a killed process or a switched account can never turn into a second payment: whatever
 * happens next, the only follow-up this repository offers is a fetch-only [recover].
 */
internal class AnswerRevealRepositoryImpl(
    private val remote: AnswerRevealRemoteDataSource,
    private val progress: QuestionProgressRepository,
) : AnswerRevealRepository {
    private val paymentLock = Mutex()

    override suspend fun quote(questionId: Long): Result<AnswerQuote> =
        resultOf(TimberLogTags.NETWORK, "Failed to quote analysis for $questionId") {
            val owner = requireNotNull(progress.current.accountKey) { LOGIN_REQUIRED }
            remote.quote(questionId).also { ensureOwner(owner) }
        }

    override suspend fun reveal(quote: AnswerQuote): Result<AnswerReveal> = transact(quote.questionId, quote)

    override suspend fun recover(questionId: Long): Result<AnswerReveal> = transact(questionId, null)

    private suspend fun transact(
        questionId: Long,
        accepted: AnswerQuote?,
    ): Result<AnswerReveal> = withContext(Dispatchers.IO) { serialised(questionId, accepted) }

    /** One charged flow at a time, so two confirmations can never both reach the payment endpoint. */
    private suspend fun serialised(
        questionId: Long,
        accepted: AnswerQuote?,
    ): Result<AnswerReveal> {
        if (!paymentLock.tryLock()) return Result.Failure(IllegalStateException(ALREADY_RUNNING))

        try {
            return attempt(questionId, accepted)
        } finally {
            paymentLock.unlock()
        }
    }

    private suspend fun attempt(
        questionId: Long,
        accepted: AnswerQuote?,
    ): Result<AnswerReveal> {
        val owner = progress.current.accountKey ?: return Result.Failure(IllegalStateException(LOGIN_REQUIRED))

        return try {
            if (!progress.hasPendingReveal(questionId)) authorise(questionId, accepted, owner)
            deliver(questionId, owner)
        } catch (cancelled: CancellationException) {
            // Deliberately keeps the latch: leaving the screen is not proof the server did not charge.
            throw cancelled
        } catch (error: IOException) {
            Result.Failure(error, afterSideEffect = progress.hasPendingReveal(questionId))
        } catch (error: IllegalStateException) {
            Result.Failure(error, afterSideEffect = progress.hasPendingReveal(questionId))
        }
    }

    /** Re-checks the price the user actually accepted, latches, then pays at most once. */
    private suspend fun authorise(
        questionId: Long,
        accepted: AnswerQuote?,
        owner: String,
    ) {
        checkNotNull(accepted) { NO_PENDING_REQUEST }
        check(accepted.cost >= 0) { INVALID_QUOTE }

        val fresh = remote.quote(questionId)
        ensureOwner(owner)
        check(fresh.isCoveredBy(accepted)) { PRICE_CHANGED }
        check(progress.setAnswerRevealPending(questionId, true, owner)) { CANNOT_PERSIST }
        ensureOwner(owner)

        if (fresh.requiresPayment) pay(questionId, owner)
    }

    private suspend fun pay(
        questionId: Long,
        owner: String,
    ) {
        try {
            remote.pay(questionId)
        } catch (error: IqResponseException) {
            // Only an explicit pre-payment rejection releases the latch. A transport or parse error
            // may follow a charge, so the next request must stay fetch-only.
            if (error.status in UNCHARGED_REJECTIONS) progress.setAnswerRevealPending(questionId, false, owner)
            throw error
        }
    }

    private suspend fun deliver(
        questionId: Long,
        owner: String,
    ): Result<AnswerReveal> {
        ensureOwner(owner)
        val reveal = remote.fetch(questionId)
        ensureOwner(owner)
        progress.recordAnswerViewed(questionId, owner)

        // Clearing the latch is best effort. Content that was already fetched (and possibly paid for)
        // must still be shown; a stale latch only ever offers another fetch-only recovery.
        if (!progress.setAnswerRevealPending(questionId, false, owner)) {
            Timber.tag(TimberLogTags.NETWORK).w("Analysis delivered for %d but the pending latch could not be cleared", questionId)
        }

        return Result.Success(reveal)
    }

    private suspend fun ensureOwner(owner: String) {
        coroutineContext.ensureActive()
        if (owner != progress.current.accountKey) throw CancellationException(ACCOUNT_CHANGED)
    }

    private companion object {
        const val LOGIN_REQUIRED = "Login required"
        const val ALREADY_RUNNING = "Analysis request already running"
        const val NO_PENDING_REQUEST = "No pending analysis request to recover"
        const val INVALID_QUOTE = "Invalid analysis quote"
        const val PRICE_CHANGED = "Analysis price changed; request a new quote"
        const val CANNOT_PERSIST = "Cannot persist pending analysis request"
        const val ACCOUNT_CHANGED = "Account changed during analysis request"
        val UNCHARGED_REJECTIONS = setOf("guest", "scoreover", "limit", "notexist", "silent", "forbidtop")
    }
}

/** A freshly granted entitlement is fine; a new or different charge needs a new confirmation. */
private fun AnswerQuote.isCoveredBy(accepted: AnswerQuote): Boolean =
    !requiresPayment || (accepted.requiresPayment && cost == accepted.cost)
