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

/** How many pages one refresh may walk before giving up and handing back what it has. */
internal const val MAX_BATCH_REQUESTS = 3

/**
 * Walks forward from [position] until it has something the user has not already seen.
 *
 * Bounded on purpose: it follows the site's own next-page links, never guesses a page number, stops
 * at a repeated cursor and gives up after [MAX_BATCH_REQUESTS] requests. Hitting that bound returns
 * the cursor it reached, so the next manual refresh continues instead of starting over.
 */
internal suspend fun fetchUnseenBatch(
    getQuestionListUseCase: GetQuestionListUseCase,
    progress: QuestionProgress,
    category: Category,
    position: FeedPosition,
    displayedIds: Set<Long>,
): Result<QuestionPage> {
    var cursor = position.nextPageUrl
    val visited = mutableSetOf<String?>()
    val excluded = position.lastQuestionIds + displayedIds
    val collected = linkedMapOf<Long, QuestionSummary>()

    repeat(MAX_BATCH_REQUESTS) {
        visited += cursor
        val result = getQuestionListUseCase(category, cursor)
        coroutineContext.ensureActive()

        when (result) {
            is Result.Failure -> {
                return result
            }
            is Result.Success -> {
                val page = result.value
                val next = page.nextPageUrl?.takeUnless { it in visited }
                page.questions.filterNot { it.id in excluded }.forEach { collected.putIfAbsent(it.id, it) }

                // Hidden questions stay in the batch so the filter can be switched back off.
                val hasVisible = collected.keys.any { !progress.hideAnswered || !progress.isSubmissionBlocked(it) }
                if (hasVisible || next == null) return Result.Success(QuestionPage(collected.values.toList(), next))
                cursor = next
            }
        }
    }

    return Result.Success(QuestionPage(collected.values.toList(), cursor))
}
