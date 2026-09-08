package com.jiugjk.iq33.library.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import timber.log.Timber

val networkModule =
    module {
        single {
            androidContext().getSharedPreferences("iq_network_prefs", Context.MODE_PRIVATE)
        }

        singleOf(::PersistentCookieJar)

        single {
            HttpLoggingInterceptor { message -> Timber.tag("Network").d(message) }.apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        }

        single {
            OkHttpClient
                .Builder()
                .cookieJar(get<PersistentCookieJar>())
                .addInterceptor(UserAgentInterceptor)
                .addInterceptor(get<HttpLoggingInterceptor>())
                .build()
        }

        singleOf(::IqHtmlClient)

        singleOf(::SessionManager)
    }

private object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain
            .request()
            .newBuilder()
            .header("User-Agent", IqConstants.USER_AGENT)
            .build()
            .let { chain.proceed(it) }
}
