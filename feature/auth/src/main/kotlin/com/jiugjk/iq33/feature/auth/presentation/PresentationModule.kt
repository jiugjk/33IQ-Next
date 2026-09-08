package com.jiugjk.iq33.feature.auth.presentation

import com.jiugjk.iq33.feature.auth.presentation.screen.login.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val presentationModule =
    module {
        viewModelOf(::LoginViewModel)
    }
