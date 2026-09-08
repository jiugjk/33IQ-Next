package com.jiugjk.iq33.app

import org.koin.dsl.module

// App-level Koin module. Shared networking/session wiring lives in library/network's
// networkModule (registered directly in IqApplication) so it can be reused without pulling in
// the app module.
val appModule = module { }
