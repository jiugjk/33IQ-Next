package com.jiugjk.iq33.feature.settings.data

import com.jiugjk.iq33.feature.settings.data.repository.ThemeRepositoryImpl
import com.jiugjk.iq33.feature.settings.domain.repository.ThemeRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

// Reuses the app-wide SharedPreferences singleton provided by library/network's networkModule.
internal val dataModule =
    module {
        singleOf(::ThemeRepositoryImpl) { bind<ThemeRepository>() }
    }
