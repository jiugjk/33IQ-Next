package com.jiugjk.iq33.feature.feed

import com.jiugjk.iq33.feature.feed.data.dataModule
import com.jiugjk.iq33.feature.feed.domain.domainModule
import com.jiugjk.iq33.feature.feed.presentation.presentationModule

val featureFeedModules =
    listOf(
        presentationModule,
        domainModule,
        dataModule,
    )
