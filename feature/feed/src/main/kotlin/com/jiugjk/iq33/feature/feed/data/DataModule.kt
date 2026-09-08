package com.jiugjk.iq33.feature.feed.data

import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionHtmlParser
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.repository.QuestionRepositoryImpl
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val dataModule =
    module {
        singleOf(::QuestionRepositoryImpl) { bind<QuestionRepository>() }
        singleOf(::QuestionRemoteDataSource)
        singleOf(::QuestionHtmlParser)
        singleOf(::QuestionJsonParser)
    }
