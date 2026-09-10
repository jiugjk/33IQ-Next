package com.jiugjk.iq33.library.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Tags the current OkHttp thread with the cookie-jar epoch at the start of a call.
 *
 * [PersistentCookieJar.saveFromResponse] runs on this same thread inside OkHttp's BridgeInterceptor,
 * so a logout that bumps the epoch while the call is in flight can refuse the late Set-Cookie.
 */
internal class CookieEpochInterceptor(
    private val cookieJar: PersistentCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val epoch = cookieJar.currentEpoch()
        EPOCH.set(epoch)

        try {
            return chain.proceed(chain.request())
        } finally {
            EPOCH.remove()
        }
    }

    companion object {
        private val EPOCH = ThreadLocal<Int>()

        fun epochForCurrentCall(): Int? = EPOCH.get()
    }
}
