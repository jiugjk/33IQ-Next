package com.jiugjk.iq33.feature.favourite.domain

import com.jiugjk.iq33.feature.favourite.domain.usecase.IsBookmarkedUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ObserveBookmarksUseCase
import com.jiugjk.iq33.feature.favourite.domain.usecase.ToggleBookmarkUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val domainModule =
    module {
        singleOf(::ObserveBookmarksUseCase)
        singleOf(::ToggleBookmarkUseCase)
        singleOf(::IsBookmarkedUseCase)
    }
