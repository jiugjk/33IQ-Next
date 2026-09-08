package com.jiugjk.iq33.app

import android.app.Application
import com.jiugjk.iq33.feature.auth.featureAuthModules
import com.jiugjk.iq33.feature.favourite.featureFavouriteModules
import com.jiugjk.iq33.feature.feed.featureFeedModules
import com.jiugjk.iq33.feature.settings.featureSettingsModules
import com.jiugjk.iq33.library.network.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import timber.log.Timber

class IqApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin()
        initTimber()
    }

    private fun initKoin() {
        GlobalContext.startKoin {
            androidLogger()
            androidContext(this@IqApplication)

            modules(networkModule)
            modules(featureFavouriteModules)
            modules(featureFeedModules)
            modules(featureAuthModules)
            modules(featureSettingsModules)
        }
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
