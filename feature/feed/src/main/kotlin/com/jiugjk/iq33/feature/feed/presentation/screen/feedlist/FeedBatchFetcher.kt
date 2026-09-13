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

/**
 * Walks forward from [position] until it has at least one visible question, or the feed ends.
 *
 * Follows the site's own next-page links, never guesses a page number, stops at a repeated cursor,
 * and gives up after [MAX_BATCH_REQUESTS] requests. Hidden cards stay in the batch so turning the
 * filter off can restore them without another network round-trip.
 */
@Suppress("LongParameterList")
internal suspend fun fetchUnseenBatch(
    getQuestionListUseCase: GetQuestionListUseCase,
    progress: QuestionProgress,
    category: Category,
    position: FeedPosition,
    displayedIds: Set<Long>,
    maxRequests: Int = MAX_BATCH_REQUESTS,
): Result<QuestionPage> {
    var cursor = position.nextPageUrl
    val visited = mutableSetOf<String?>()
    val excluded = position.lastQuestionIds + displayedIds
    val collected = linkedMapOf<Long, QuestionSummary>()

    repeat(maxRequests) {
        visited += cursor
        val result = getQuestionListUseCase(category, cursor)
        coroutineContext.ensureActive()

        when (result) {
            is Result.Failure -> {
                return if (collected.isEmpty()) result else Result.Success(QuestionPage(collected.values.toList(), cursor))
            }
            is Result.Success -> {
                val page = result.value
                val next = page.nextPageUrl?.takeUnless { it in visited }
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
