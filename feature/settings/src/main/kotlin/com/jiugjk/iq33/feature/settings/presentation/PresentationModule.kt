package com.jiugjk.iq33.feature.settings.presentation

import com.jiugjk.iq33.feature.settings.presentation.screen.aboutlibraries.AboutLibrariesViewModel
import com.jiugjk.iq33.feature.settings.presentation.screen.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val presentationModule =
    module {
        viewModelOf(::SettingsViewModel)
        viewModelOf(::AboutLibrariesViewModel)
    }
