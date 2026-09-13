package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** How many consecutive page requests one filtered walk may make. */
internal const val MAX_BATCH_REQUESTS = 5

/** How many requests one *continuous* automatic walk may make, across batches. */
internal const val MAX_CHAIN_REQUESTS = 20

/**
 * Cursors already requested during one continuous automatic load, plus that load's request budget.
 *
 * Lives in the view model, not in a single batch: a per-batch set only notices a cycle shorter than
 * the batch, so a longer loop (A→B→C→D→E→F→A) walked five pages at a time would keep "finding" a new
 * cursor forever while the hide-filter left the screen empty. The budget is what bounds the whole
 * chain; it is reset by a refresh, a category switch, an account change, or the user's own
 * "keep loading" action.
 */
internal class FeedWalk(
    private val maxRequests: Int = MAX_CHAIN_REQUESTS,
) {
    private val visited = mutableSetOf<String?>()
    private var used = 0

    /** The chain has spent its budget; only an explicit user action may extend it. */
    val isExhausted: Boolean get() = used >= maxRequests

    fun hasVisited(cursor: String?): Boolean = cursor in visited

    fun visit(cursor: String?): Visit {
        if (used >= maxRequests) return Visit.BUDGET_EXHAUSTED
        if (!visited.add(cursor)) return Visit.REPEATED
        used++

        return Visit.ALLOWED
    }

    /**
     * Releases a cursor whose request did not produce a page.
     *
     * A failed request never consumed that cursor, so a retry has to be able to ask for it again -
     * only the chain's budget is spent.
     */
    fun forget(cursor: String?) {
        visited -= cursor
    }

    fun reset() {
        visited.clear()
        used = 0
    }

    enum class Visit {
        ALLOWED,

        /** This cursor was already requested in this chain: the feed's own links form a cycle. */
        REPEATED,

        BUDGET_EXHAUSTED,
    }
}

/**
 * Walks forward from [position] until it has at least one visible question, or the feed ends.
 *
 * Follows the site's own next-page links, never guesses a page number, stops at any cursor already
 * seen in [walk] - including one from an earlier batch - and gives up after [maxRequests] requests
 * in this batch or when the chain's budget runs out. Hidden cards stay in the batch so turning the
 * filter off can restore them without another network round-trip.
 *
 * A cycle ends paging (`nextPageUrl = null`); an exhausted budget keeps the unvisited cursor, so the
 * user can decide to continue.
 */
@Suppress("LongParameterList", "ReturnCount")
internal suspend fun fetchUnseenBatch(
    getQuestionListUseCase: GetQuestionListUseCase,
    progress: QuestionProgress,
    category: Category,
    position: FeedPosition,
    displayedIds: Set<Long>,
    walk: FeedWalk = FeedWalk(),
    maxRequests: Int = MAX_BATCH_REQUESTS,
): Result<QuestionPage> {
    var cursor = position.nextPageUrl
    val excluded = position.lastQuestionIds + displayedIds
    val collected = linkedMapOf<Long, QuestionSummary>()

    repeat(maxRequests) {
        val visit = walk.visit(cursor)
        if (visit != FeedWalk.Visit.ALLOWED) {
            // A cycle ends paging for good; a spent budget keeps the cursor for the user to resume.
            val endCursor = cursor.takeIf { visit == FeedWalk.Visit.BUDGET_EXHAUSTED }
            return Result.Success(QuestionPage(collected.values.toList(), endCursor))
        }

        val result = getQuestionListUseCase(category, cursor)
        coroutineContext.ensureActive()

        when (result) {
            is Result.Failure -> {
                walk.forget(cursor)
                return if (collected.isEmpty()) result else Result.Success(QuestionPage(collected.values.toList(), cursor))
            }
            is Result.Success -> {
                val page = result.value
                val next = page.nextPageUrl?.takeUnless { walk.hasVisited(it) }
                page.questions.filterNot { it.id in excluded }.forEach { collected.putIfAbsent(it.id, it) }

                val visibleCount =
                    collected.keys.count { id -> !progress.hideAnswered || !progress.isSubmissionBlocked(id) }
                if (visibleCount > 0 || next == null) {
                    return Result.Success(QuestionPage(collected.values.toList(), next))
                }
                cursor = next
            }
        }
    }

    return Result.Success(QuestionPage(collected.values.toList(), cursor))
}
