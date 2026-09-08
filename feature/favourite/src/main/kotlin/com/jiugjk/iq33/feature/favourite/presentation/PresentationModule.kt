package com.jiugjk.iq33.feature.favourite.presentation

import com.jiugjk.iq33.feature.favourite.presentation.screen.favourite.FavouriteViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val presentationModule =
    module {
        viewModelOf(::FavouriteViewModel)
    }
