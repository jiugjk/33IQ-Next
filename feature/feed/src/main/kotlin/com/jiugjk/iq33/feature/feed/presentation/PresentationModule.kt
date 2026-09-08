package com.jiugjk.iq33.feature.feed.presentation

import com.jiugjk.iq33.feature.feed.presentation.screen.feedlist.FeedListViewModel
import com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail.QuestionDetailViewModel
import com.jiugjk.iq33.feature.feed.presentation.screen.search.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val presentationModule =
    module {
        viewModelOf(::FeedListViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::QuestionDetailViewModel)
    }
