package com.jiugjk.iq33.feature.settings

import com.jiugjk.iq33.feature.settings.data.dataModule
import com.jiugjk.iq33.feature.settings.domain.domainModule
import com.jiugjk.iq33.feature.settings.presentation.presentationModule

val featureSettingsModules =
    listOf(
        presentationModule,
        domainModule,
        dataModule,
    )
