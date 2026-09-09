package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * One slot of side work on the question detail screen - a submission, a bookmark write, a paid
 * reveal - of which at most one may ever be in flight.
 *
 * Both guards are needed. [isActive] rejects a tap while the previous request is still running, and
 * the mutex rejects the second of two taps delivered in the same frame, before the state has moved
 * and before [isActive] has become true. Eligibility is then re-checked *inside* the lock by
 * [isEligible], against the state as it is when the work really starts and against the question it
 * was requested for, so a reload or a competing flow in between drops the work instead of paying
 * for it a second time.
 */
internal class SideWorkSlot(
    private val scope: CoroutineScope,
    private val currentState: () -> QuestionDetailUiState,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    /** Runs [work] for [questionId], provided the state still satisfies [guard] once the lock is held. */
    fun start(
        questionId: Long,
        guard: (QuestionDetailUiState.Content) -> Boolean,
        work: suspend () -> Unit,
    ) {
        job =
            scope.launch {
                if (mutex.tryLock()) {
                    try {
                        if (isEligible(questionId, guard)) work()
                    } finally {
                        mutex.unlock()
                    }
                }
            }
    }

    /** Whether the screen is still showing [questionId] in a state [guard] accepts. */
    fun isEligible(
        questionId: Long,
        guard: (QuestionDetailUiState.Content) -> Boolean,
    ): Boolean {
        val content = currentState() as? QuestionDetailUiState.Content ?: return false

        return content.detail.id == questionId && guard(content)
    }

    fun cancel() {
        job?.cancel()
    }
}
