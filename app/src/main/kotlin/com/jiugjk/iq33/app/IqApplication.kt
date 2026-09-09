package com.jiugjk.iq33.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.jiugjk.iq33.feature.auth.featureAuthModules
import com.jiugjk.iq33.feature.favourite.featureFavouriteModules
import com.jiugjk.iq33.feature.feed.featureFeedModules
import com.jiugjk.iq33.feature.settings.featureSettingsModules
import com.jiugjk.iq33.library.network.networkModule
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import timber.log.Timber

class IqApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin()
        initImageLoader()
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

    private fun initImageLoader() {
        val okHttpClient: OkHttpClient = get()

        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
                .build()
        }
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
