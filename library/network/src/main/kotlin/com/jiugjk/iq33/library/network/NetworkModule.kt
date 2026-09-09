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
import java.util.concurrent.TimeUnit

val networkModule =
    module {
        single {
            androidContext().getSharedPreferences("iq_network_prefs", Context.MODE_PRIVATE)
        }

        singleOf(::PersistentCookieJar)

        single {
            // BASIC logs method/URL only. Never raise this to BODY: login form fields would leak.
            HttpLoggingInterceptor { message -> Timber.tag(TIMBER_LOG_TAG_NETWORK).d(message) }.apply {
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
                .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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

private const val HTTP_TIMEOUT_SECONDS = 30L

private const val TIMBER_LOG_TAG_NETWORK = "Network"
