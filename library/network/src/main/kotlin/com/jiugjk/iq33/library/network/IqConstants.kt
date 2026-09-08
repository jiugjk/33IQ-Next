package com.jiugjk.iq33.library.network

/*
 * 33IQ (https://www.33iq.com) has no official public API. These constants and the rest of this
 * module were derived from two sources:
 *
 * 1. The publicly served HTML/JS of the mobile-optimized website (the site itself renders pages
 *    server-side using the GBK encoding declared in its own <meta charset>).
 * 2. A HAR capture of the real Android app's traffic, provided by the project owner. It revealed
 *    that several of the *same* URLs the website serves as HTML instead return rich JSON when an
 *    extra `p` query parameter (a per-endpoint "platform"-ish flag, exact meaning unconfirmed) is
 *    present, e.g. `/question/<id>.html?p=3`. This was verified live against the real server before
 *    being relied on here - see [QUESTION_DETAIL_APP_P_PARAM].
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

    // Confirmed live: GET $QUESTION_DETAIL_URL/<id>.html?p=3 returns a JSON array (not HTML) with
    // the full question payload (choices, tags, stats, ...). See QuestionJsonParser.
    const val QUESTION_DETAIL_APP_P_PARAM = "3"

    // The app's own "am I logged in" signal: this endpoint replies {"status":"guest"} for guests
    // and (presumably) real task data otherwise - confirmed live for the guest case.
    const val GUEST_PROBE_URL = "$BASE_URL/app/taskall"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36 33iqNext/1.0"
}
