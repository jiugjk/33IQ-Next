package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@Suppress("TooManyFunctions")
internal class FeedListViewModel(
    private val getQuestionListUseCase: GetQuestionListUseCase,
    private val questionProgressRepository: QuestionProgressRepository,
) : BaseViewModel<FeedListUiState, FeedListAction>(FeedListUiState.Loading(progress = questionProgressRepository.current)) {
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var accountKey = questionProgressRepository.current.accountKey

    /**
     * Cursors and request budget of the current continuous automatic load.
     *
     * Shared by every batch of one chain, so a page cycle longer than a single batch is detected and
     * an endless "filtered everything away, fetch the next page" loop is bounded.
     */
    private val walk = FeedWalk()

    init {
        viewModelScope.launch {
            questionProgressRepository.progress.collect { progress ->
                val accountChanged = accountKey != progress.accountKey
                accountKey = progress.accountKey
                sendAction(FeedListAction.ProgressChanged(progress))
                if (accountChanged) {
                    selectCategory(uiStateFlow.value.selectedCategory)
                } else {
                    maybeContinueFilteredPaging()
                }
            }
        }
    }

    fun onEvent(event: FeedListEvent) {
        when (event) {
            is FeedListEvent.CategorySelected -> {
                selectCategory(event.category)
            }
            is FeedListEvent.HideAnsweredChanged -> {
                questionProgressRepository.setHideAnswered(event.hide)
            }
            FeedListEvent.Refreshed -> {
                refresh()
            }
            FeedListEvent.EndReached -> {
                loadMore(retry = false)
            }
            FeedListEvent.LoadMoreRetried -> {
                loadMore(retry = true)
            }
            FeedListEvent.ContinuePagingRequested -> {
                // The user explicitly asked for more, so the chain gets a fresh budget. The paused
                // flag is cleared by the load itself, so it is still set while eligibility is checked.
                walk.reset()
                loadMore(retry = false, userRequested = true)
            }
            FeedListEvent.ShowAllQuestions -> {
                questionProgressRepository.setHideAnswered(false)
                refresh()
            }
        }
    }

    fun onInit() {
        val state = uiStateFlow.value
        if (state is FeedListUiState.Loading && loadJob?.isActive != true) selectCategory(state.selectedCategory)
    }

    private fun selectCategory(category: Category) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        sendAction(FeedListAction.LoadStart(category))
        startFreshBatch(category)
    }

    private fun refresh() {
        val current = uiStateFlow.value
        if (loadJob?.isActive == true) return
        if (current !is FeedListUiState.Content) {
            selectCategory(current.selectedCategory)
            return
        }
        loadMoreJob?.cancel()
        sendAction(FeedListAction.RefreshStart(current.selectedCategory))
        startFreshBatch(current.selectedCategory)
    }

    private fun startFreshBatch(category: Category) {
        // A refresh, a category switch and an account change all start a new chain.
        walk.reset()
        val owner = questionProgressRepository.current.accountKey
        // Refresh and reopen always start at the first page; previously seen cards are not hidden.
        val position = FeedPosition()
        loadJob =
            viewModelScope
                .launch {
                    val result =
                        fetchUnseenBatch(
                            getQuestionListUseCase = getQuestionListUseCase,
                            progress = questionProgressRepository.current,
                            category = category,
                            position = position,
                            displayedIds = emptySet(),
                            walk = walk,
                        )
                    coroutineContext.ensureActive()
                    if (owner == questionProgressRepository.current.accountKey) {
                        applyBatch(category, result)
                    }
                }.also { job ->
                    job.invokeOnCompletion { error ->
                        if (error == null) maybeContinueFilteredPaging()
                    }
                }
    }

    private fun applyBatch(
        category: Category,
        result: Result<QuestionPage>,
    ) {
        when (result) {
            is Result.Success -> {
                val page = result.value
                sendAction(FeedListAction.LoadSuccess(category, page.questions, page.nextPageUrl, page.nextPageUrl != null))
            }
            is Result.Failure -> {
                sendAction(
                    if (uiStateFlow.value is FeedListUiState.Content) {
                        FeedListAction.RefreshFailure(category)
                    } else {
                        FeedListAction.LoadFailure(category)
                    },
                )
            }
        }
    }

    private fun loadMore(
        retry: Boolean,
        userRequested: Boolean = false,
    ) {
        val state = uiStateFlow.value as? FeedListUiState.Content ?: return
        val busy = loadMoreJob?.isActive == true || loadJob?.isActive == true
        val allowed =
            when {
                userRequested -> state.canContinuePaging
                retry -> state.canRetryLoadMore
                else -> state.canStartLoadMore
            }
        val cursor = state.nextPageUrl
        if (busy || !allowed || cursor == null) return
        val category = state.selectedCategory
        val owner = questionProgressRepository.current.accountKey
        val request = LoadMoreRequest(category, state.page + 1)
        val alreadyListed = state.questions.map { it.id }.toSet()
        sendAction(FeedListAction.LoadMoreStart(category))
        loadMoreJob =
            viewModelScope
                .launch {
                    val result =
                        fetchUnseenBatch(
                            getQuestionListUseCase = getQuestionListUseCase,
                            progress = questionProgressRepository.current,
                            category = category,
                            position = FeedPosition(nextPageUrl = cursor),
                            displayedIds = alreadyListed,
                            walk = walk,
                        )
                    coroutineContext.ensureActive()
                    if (owner == questionProgressRepository.current.accountKey) applyLoadMore(request, result)
                }.also { job ->
                    job.invokeOnCompletion { error ->
                        if (error == null) maybeContinueFilteredPaging()
                    }
                }
    }

    private fun applyLoadMore(
        request: LoadMoreRequest,
        result: Result<QuestionPage>,
    ) {
        when (result) {
            is Result.Success -> {
                val page = result.value
                sendAction(
                    FeedListAction.LoadMoreSuccess(
                        request.category,
                        request.nextPage,
                        page.questions,
                        page.nextPageUrl,
                        page.nextPageUrl != null,
                    ),
                )
            }
            is Result.Failure -> {
                sendAction(FeedListAction.LoadMoreFailure(request.category))
            }
        }
    }

    /**
     * When the hide-filter leaves the screen empty but more pages exist, keep walking automatically.
     *
     * Guarded by canStartLoadMore (cursor present, idle, not failed) *and* by the chain budget: a
     * feed whose next-page links cycle can otherwise keep this going forever without ever producing
     * a visible question. When the budget is spent the loop stops and hands the decision to the user
     * instead of silently continuing or silently giving up.
     */
    private fun maybeContinueFilteredPaging() {
        val state = uiStateFlow.value as? FeedListUiState.Content ?: return
        if (!state.canStartLoadMore || state.nextPageUrl == null) return

        if (walk.isExhausted) {
            sendAction(FeedListAction.AutoPagingPaused(state.selectedCategory))
        } else if (state.visibleQuestions.isEmpty()) {
            loadMore(retry = false)
        }
    }

    private data class LoadMoreRequest(
        val category: Category,
        val nextPage: Int,
    )
}
