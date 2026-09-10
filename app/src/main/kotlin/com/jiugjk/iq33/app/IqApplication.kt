package com.jiugjk.iq33.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.jiugjk.iq33.feature.auth.featureAuthModules
import com.jiugjk.iq33.feature.favourite.featureFavouriteModules
import com.jiugjk.iq33.feature.feed.featureFeedModules
import com.jiugjk.iq33.feature.settings.featureSettingsModules
import com.jiugjk.iq33.library.network.DailyCheckIn
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import com.jiugjk.iq33.library.network.networkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import timber.log.Timber

class IqApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        initKoin()
        initImageLoader()
        initTimber()
        startDailyCheckIn()
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

    /**
     * Re-verifies the session, then claims the daily check-in whenever that session is
     * authenticated. A guest status clears the local "already done today" mark so a later login
     * (possibly a different account) can still claim.
     *
     * Collecting [SessionManager.sessionFlow] is what also covers a login that happens after start:
     * the login path updates the flow, and [DailyCheckIn.runIfDue] is itself once-per-day.
     */
    private fun startDailyCheckIn() {
        val sessionManager: SessionManager = get()
        val dailyCheckIn: DailyCheckIn = get()

        applicationScope.launch {
            sessionManager.refreshFromServer()
        }
        applicationScope.launch {
            sessionManager.sessionFlow.collect { session ->
                if (session.isLoggedIn) {
                    dailyCheckIn.runIfDue()
                } else if (session.status == SessionStatus.GUEST) {
                    dailyCheckIn.clearLocalMark()
                }
            }
        }
    }
}
