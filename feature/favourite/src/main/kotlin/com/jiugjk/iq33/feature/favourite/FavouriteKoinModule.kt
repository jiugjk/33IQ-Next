package com.jiugjk.iq33.feature.favourite

import com.jiugjk.iq33.feature.favourite.data.dataModule
import com.jiugjk.iq33.feature.favourite.domain.domainModule
import com.jiugjk.iq33.feature.favourite.presentation.presentationModule

val featureFavouriteModules =
    listOf(
        presentationModule,
        domainModule,
        dataModule,
    )
