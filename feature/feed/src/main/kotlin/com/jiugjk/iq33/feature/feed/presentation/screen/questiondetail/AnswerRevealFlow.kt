package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.AnswerRevealUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Drives 查看解析: ask the price, wait for the user, then reveal exactly once.
 *
 * Split out of [QuestionDetailViewModel] because this is the only flow on the screen that can spend
 * the account's 学识, and it has its own rules - a quote is never acted on by itself, a request that
 * may already have been charged is only ever followed by a fetch-only recovery, and a reply that
 * arrives after the account changed is dropped rather than applied.
 */
internal class AnswerRevealFlow(
    scope: CoroutineScope,
    currentState: () -> QuestionDetailUiState,
    private val answerRevealUseCases: AnswerRevealUseCases,
    private val questionProgressRepository: QuestionProgressRepository,
    private val sendAction: (QuestionDetailAction) -> Unit,
) {
    private val quoting = SideWorkSlot(scope, currentState)
    private val revealing = SideWorkSlot(scope, currentState)

    val slots: List<SideWorkSlot> = listOf(quoting, revealing)

    /** Read-only: asks what this account would be charged, and never reveals on its own. */
    fun requestQuote(content: QuestionDetailUiState.Content?) {
        if (content?.canStartAnswerReveal != true || quoting.isActive) return

        val id = content.detail.id

        quoting.start(id, { it.canStartAnswerReveal }) {
            sendAction(AnswerRevealAction.QuoteStarted(id))
            val owner = questionProgressRepository.current.accountKey
            val result = answerRevealUseCases.quote(id)
            coroutineContext.ensureActive()
            if (owner == questionProgressRepository.current.accountKey) applyQuote(id, result)
        }
    }

    /**
     * Reveals the answer. [recovery] resumes a request that may already have been paid for, which
     * only ever re-fetches; a normal confirmation must carry the quote the dialog actually showed.
     */
    fun reveal(
        content: QuestionDetailUiState.Content?,
        recovery: Boolean,
    ) {
        if (content == null || revealing.isActive) return

        val allowed = if (recovery) content.canRecoverAnswerReveal else content.canConfirmAnswerReveal
        val id = content.detail.id
        val quote = (content.answerReveal as? RevealState.QuoteReady)?.quote
        val confirmable = recovery || (quote != null && quote.questionId == id)
        if (!allowed || !confirmable) return

        revealing.start(id, { if (recovery) it.canRecoverAnswerReveal else it.canConfirmAnswerReveal }) {
            sendAction(AnswerRevealAction.Started(id, recovery))
            // The reducer decides whether the reveal really started; if it refused, nothing is sent.
            if (revealing.isEligible(id) { it.answerReveal is RevealState.Revealing }) {
                perform(id, recovery, quote)
            }
        }
    }

    private suspend fun perform(
        id: Long,
        recovery: Boolean,
        quote: AnswerQuote?,
    ) {
        val owner = questionProgressRepository.current.accountKey
        val result = if (recovery) answerRevealUseCases.recover(id) else answerRevealUseCases.reveal(requireNotNull(quote))
        coroutineContext.ensureActive()
        if (owner != questionProgressRepository.current.accountKey) return

        when (result) {
            is Result.Success -> sendAction(AnswerRevealAction.Finished(id, result.value))
            is Result.Failure -> sendAction(AnswerRevealAction.Failed(id, result.afterSideEffect))
        }
    }

    private fun applyQuote(
        id: Long,
        result: Result<AnswerQuote>,
    ) {
        when (result) {
            is Result.Success -> sendAction(AnswerRevealAction.QuoteReady(result.value))
            is Result.Failure -> sendAction(AnswerRevealAction.Failed(id, result.afterSideEffect))
        }
    }
}
