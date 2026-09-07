package com.jiugjk.iq33.feature.feed.domain

import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.SearchQuestionsUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val domainModule =
    module {
        singleOf(::GetQuestionListUseCase)
        singleOf(::SearchQuestionsUseCase)
        singleOf(::GetQuestionDetailUseCase)
    }
