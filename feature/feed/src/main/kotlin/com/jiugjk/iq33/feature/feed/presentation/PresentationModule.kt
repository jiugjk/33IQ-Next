package com.jiugjk.iq33.feature.feed.presentation

import com.jiugjk.iq33.feature.feed.presentation.screen.feedlist.FeedListViewModel
import com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer.ImageSaver
import com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail.QuestionDetailViewModel
import com.jiugjk.iq33.feature.feed.presentation.screen.search.SearchViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Dispatcher for the blocking file and MediaStore work behind saving an image. Separate from the
 * feed's parsing dispatcher because this is I/O, not CPU: it belongs on the elastic IO pool rather
 * than the CPU-bound default one.
 */
internal val imageIoDispatcherQualifier = named("feedImageIoDispatcher")

internal val presentationModule =
    module {
        single<CoroutineDispatcher>(imageIoDispatcherQualifier) { Dispatchers.IO }

        viewModelOf(::FeedListViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::QuestionDetailViewModel)

        single { ImageSaver(ioDispatcher = get(imageIoDispatcherQualifier)) }
    }
