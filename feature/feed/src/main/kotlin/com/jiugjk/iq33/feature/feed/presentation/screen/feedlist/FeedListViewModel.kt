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

internal class FeedListViewModel(
    private val getQuestionListUseCase: GetQuestionListUseCase,
    private val questionProgressRepository: QuestionProgressRepository,
) : BaseViewModel<FeedListUiState, FeedListAction>(FeedListUiState.Loading(progress = questionProgressRepository.current)) {
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var lastLoadedAt = 0L
    private var accountKey = questionProgressRepository.current.accountKey

    init {
        viewModelScope.launch {
            questionProgressRepository.progress.collect { progress ->
                val accountChanged = accountKey != progress.accountKey
                accountKey = progress.accountKey
                sendAction(FeedListAction.ProgressChanged(progress))
                if (accountChanged) selectCategory(uiStateFlow.value.selectedCategory)
            }
        }
    }

    fun onEvent(event: FeedListEvent) {
        when (event) {
            is FeedListEvent.CategorySelected -> selectCategory(event.category)
            is FeedListEvent.HideAnsweredChanged -> questionProgressRepository.setHideAnswered(event.hide)
            FeedListEvent.Refreshed -> refresh()
            FeedListEvent.EndReached -> loadMore(retry = false)
            FeedListEvent.LoadMoreRetried -> loadMore(retry = true)
        }
    }

    fun onInit() {
        val state = uiStateFlow.value
        if (state is FeedListUiState.Loading && loadJob?.isActive != true) selectCategory(state.selectedCategory)
    }

    /** A warm reopen after a long absence should behave like a cold start, not keep an hours-old batch. */
    fun onForeground(now: Long = System.currentTimeMillis()) {
        if (uiStateFlow.value is FeedListUiState.Content && now - lastLoadedAt >= STALE_AFTER_MS) refresh()
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
        val owner = questionProgressRepository.current.accountKey
        val position = questionProgressRepository.feedPosition(category.id)
        val displayedIds =
            (uiStateFlow.value as? FeedListUiState.Content)
                ?.questions
                .orEmpty()
                .map { it.id }
                .toSet()
        loadJob =
            viewModelScope.launch {
                val result =
                    fetchUnseenBatch(
                        getQuestionListUseCase = getQuestionListUseCase,
                        progress = questionProgressRepository.current,
                        category = category,
                        position = position,
                        displayedIds = displayedIds,
                    )
                coroutineContext.ensureActive()
                if (owner == questionProgressRepository.current.accountKey) {
                    applyBatch(category, position, owner, result)
                }
            }
    }

    private fun applyBatch(
        category: Category,
        position: FeedPosition,
        owner: String?,
        result: Result<QuestionPage>,
    ) {
        when (result) {
            is Result.Success -> {
                val page = result.value
                savePosition(category, page, position, owner)
                lastLoadedAt = System.currentTimeMillis()
                sendAction(
                    if (page.questions.isEmpty()) {
                        FeedListAction.NoNewContent(category, page.nextPageUrl)
                    } else {
                        FeedListAction.LoadSuccess(category, page.questions, page.nextPageUrl, page.nextPageUrl != null)
                    },
                )
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

    private fun loadMore(retry: Boolean) {
        val state = uiStateFlow.value as? FeedListUiState.Content ?: return
        val busy = loadMoreJob?.isActive == true || loadJob?.isActive == true
        val allowed = if (retry) state.canRetryLoadMore else state.canStartLoadMore
        val cursor = state.nextPageUrl
        if (busy || !allowed || cursor == null) return
        val category = state.selectedCategory
        val owner = questionProgressRepository.current.accountKey
        val position = questionProgressRepository.feedPosition(category.id)
        val request = LoadMoreRequest(category, position, owner, cursor, state.page + 1)
        sendAction(FeedListAction.LoadMoreStart(category))
        loadMoreJob =
            viewModelScope.launch {
                val result = getQuestionListUseCase(category, cursor)
                coroutineContext.ensureActive()
                if (owner == questionProgressRepository.current.accountKey) applyLoadMore(request, result)
            }
    }

    private fun applyLoadMore(
        request: LoadMoreRequest,
        result: Result<QuestionPage>,
    ) {
        when (result) {
            is Result.Success -> {
                // A next link identical to the cursor just used would page in place forever.
                val page = result.value.let { it.copy(nextPageUrl = it.nextPageUrl?.takeUnless { url -> url == request.cursor }) }
                savePosition(request.category, page, request.position, request.owner)
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

    private fun savePosition(
        category: Category,
        page: QuestionPage,
        previous: FeedPosition,
        owner: String?,
    ) {
        val recentIds = (previous.lastQuestionIds + page.questions.map { it.id }).toList().takeLast(MAX_RECENT_IDS).toSet()
        questionProgressRepository.saveFeedPosition(category.id, FeedPosition(page.nextPageUrl, recentIds), owner)
    }

    /** One "load more" in flight, captured so a late reply cannot be applied to a newer cursor. */
    private data class LoadMoreRequest(
        val category: Category,
        val position: FeedPosition,
        val owner: String?,
        val cursor: String,
        val nextPage: Int,
    )

    private companion object {
        const val MAX_RECENT_IDS = 300
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
