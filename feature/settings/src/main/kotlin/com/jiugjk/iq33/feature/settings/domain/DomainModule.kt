package com.jiugjk.iq33.feature.settings.domain

import com.jiugjk.iq33.feature.settings.domain.usecase.ObserveThemeModeUseCase
import com.jiugjk.iq33.feature.settings.domain.usecase.SetThemeModeUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val domainModule =
    module {
        singleOf(::ObserveThemeModeUseCase)
        singleOf(::SetThemeModeUseCase)
    }
