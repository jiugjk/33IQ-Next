package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import kotlinx.coroutines.launch

internal class QuestionDetailViewModel(
    private val getQuestionDetailUseCase: GetQuestionDetailUseCase,
    private val isBookmarkedUseCase: IsBookmarkedUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
) : BaseViewModel<QuestionDetailUiState, QuestionDetailAction>(QuestionDetailUiState.Loading) {
    fun load(id: Long) {
        sendAction(QuestionDetailAction.LoadStart)

        viewModelScope.launch {
            when (val result = getQuestionDetailUseCase(id)) {
                is Result.Success -> {
                    val isBookmarked = isBookmarkedUseCase(id)
                    sendAction(QuestionDetailAction.LoadSuccess(result.value, isBookmarked))
                }
                is Result.Failure -> sendAction(QuestionDetailAction.LoadFailure)
            }
        }
    }

    fun onChoiceSelected(choiceId: String) {
        sendAction(QuestionDetailAction.ChoiceSelected(choiceId))
    }

    fun onBookmarkClick(detail: QuestionDetail) {
        viewModelScope.launch {
            val savedQuestion =
                SavedQuestion(
                    id = detail.id,
                    title = detail.title,
                    tags = detail.tags,
                    savedAt = System.currentTimeMillis(),
                )
            val isBookmarked = toggleBookmarkUseCase(savedQuestion)

            sendAction(QuestionDetailAction.BookmarkChanged(isBookmarked))
        }
    }
}
