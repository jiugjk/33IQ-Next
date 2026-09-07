package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

@Immutable
internal sealed interface QuestionDetailUiState : BaseState {
    @Immutable
    data object Loading : QuestionDetailUiState

    @Immutable
    data object Error : QuestionDetailUiState

    @Immutable
    data class Content(
        val detail: QuestionDetail,
        val isBookmarked: Boolean,
        val selectedChoiceId: String? = null,
    ) : QuestionDetailUiState
}
