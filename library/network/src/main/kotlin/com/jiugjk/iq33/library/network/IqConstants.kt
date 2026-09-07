package com.jiugjk.iq33.library.network

/*
 * 33IQ (https://www.33iq.com) has no official public API. These constants and the rest of this
 * module were derived by inspecting the publicly served HTML/JS of the mobile-optimized website
 * (the site itself renders pages server-side using the GBK encoding declared in its own <meta charset>).
 *
 * This client is an unofficial, personal-learning project and is not affiliated with 33IQ.
 */
object IqConstants {
    const val BASE_URL = "https://www.33iq.com"
    const val ASSET_HOST = "https://a.33iq.com"

    // The site's server-rendered HTML is served as GBK, not UTF-8.
    const val PAGE_CHARSET = "GBK"

    const val LOGIN_URL = "$BASE_URL/index/login"
    const val SEARCH_URL = "$BASE_URL/index/search"
    const val QUESTION_LIST_URL = "$BASE_URL/question/"
    const val QUESTION_DETAIL_URL = "$BASE_URL/question"
    const val PROFILE_URL = "$BASE_URL/showprofile/id-%s.html"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36 33iqNext/1.0"
}
