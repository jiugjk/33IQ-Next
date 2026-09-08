package com.jiugjk.iq33.feature.feed.domain

import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionDetailUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.PraiseQuestionUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuestionAnswerUseCases
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.QuoteHintUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RevealAnswerUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.RevealHintUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.SearchQuestionsUseCase
import com.jiugjk.iq33.feature.feed.domain.usecase.SubmitAnswerUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val domainModule =
    module {
        singleOf(::GetQuestionListUseCase)
        singleOf(::SearchQuestionsUseCase)
        singleOf(::GetQuestionDetailUseCase)
        singleOf(::SubmitAnswerUseCase)
        singleOf(::QuoteAnswerUseCase)
        singleOf(::RevealAnswerUseCase)
        singleOf(::QuoteHintUseCase)
        singleOf(::RevealHintUseCase)
        singleOf(::PraiseQuestionUseCase)
        singleOf(::QuestionAnswerUseCases)
    }
