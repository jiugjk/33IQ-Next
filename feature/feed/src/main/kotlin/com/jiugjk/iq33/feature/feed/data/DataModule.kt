package com.jiugjk.iq33.feature.feed.data

import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRevealRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.repository.AnswerRevealRepositoryImpl
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRevealRepository
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionHtmlParser
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.repository.AnswerRepositoryImpl
import com.jiugjk.iq33.feature.feed.data.repository.QuestionRepositoryImpl
import com.jiugjk.iq33.feature.feed.data.repository.QuestionProgressRepositoryImpl
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Dispatcher used for HTML/JSON parsing and model mapping. Network I/O stays on the IO dispatcher
 * inside the HTTP client; this is the CPU work that follows, kept off the caller's (main) thread.
 * Injected rather than hard-coded so tests can substitute a deterministic dispatcher.
 */
internal val parsingDispatcherQualifier = named("feedParsingDispatcher")

internal val dataModule =
    module {
        single<CoroutineDispatcher>(parsingDispatcherQualifier) { Dispatchers.Default }

        singleOf(::QuestionProgressRepositoryImpl) { bind<QuestionProgressRepository>() }
        singleOf(::QuestionRepositoryImpl) { bind<QuestionRepository>() }
        single {
            QuestionRemoteDataSource(
                htmlClient = get(),
                htmlParser = get(),
                jsonParser = get(),
                parsingDispatcher = get(parsingDispatcherQualifier),
            )
        }
        singleOf(::QuestionHtmlParser)
        singleOf(::QuestionJsonParser)
        singleOf(::AnswerRepositoryImpl) { bind<AnswerRepository>() }
        singleOf(::AnswerRevealRepositoryImpl) { bind<AnswerRevealRepository>() }
        single { AnswerRevealRemoteDataSource(htmlClient = get(), parsingDispatcher = get(parsingDispatcherQualifier)) }
        single {
            AnswerRemoteDataSource(
                htmlClient = get(),
                parsingDispatcher = get(parsingDispatcherQualifier),
            )
        }
    }
