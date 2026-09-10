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

    // The site's server-rendered HTML is served as GBK, not UTF-8.
    const val PAGE_CHARSET = "GBK"

    const val LOGIN_URL = "$BASE_URL/index/login"
    const val SEARCH_URL = "$BASE_URL/index/search"
    const val QUESTION_LIST_URL = "$BASE_URL/question/"
    const val QUESTION_DETAIL_URL = "$BASE_URL/question"

    // Confirmed live: GET $QUESTION_DETAIL_URL/<id>.html?p=3 returns a JSON array (not HTML) with
    // the full question payload (choices, tags, stats, ...). See QuestionJsonParser.
    const val QUESTION_DETAIL_APP_P_PARAM = "3"

    // The app's own "am I logged in" signal: this endpoint replies {"status":"guest"} for guests
    // and (presumably) real task data otherwise - confirmed live for the guest case.
    const val GUEST_PROBE_URL = "$BASE_URL/app/taskall"

    // Answer-submission / paid-reveal endpoints, confirmed from a follow-up HAR capture of a real
    // logged-in Android app session actually submitting answers, buying hints and viewing answers.
    // All take a single `q_id` (or, for submission, `id`) form field. See AnswerRemoteDataSource.
    //
    // Every one of them was called by the real app with `?p=1&lang=zh-cn` on the URL (in addition to
    // the form body) - unlike the question-detail endpoint's `p=3`, this `p=1` was identical across
    // all of these action endpoints. An earlier version of this client dropped it, which broke every
    // one of these calls at runtime ("网络异常" from a real user report) - so it's required, not just
    // cosmetic like the `time=<cache-busting timestamp>` param the real app also adds.
    const val ACTION_QUERY_SUFFIX = "?p=1&lang=zh-cn"

    // Submits an answer. Despite the name/shape (it is 33IQ's generic "post a comment" endpoint,
    // reused for answers via isanswer=1), this is confirmed to score the account and reject a
    // second submission for the same question with {"status":"repeat"}.
    const val SUBMIT_ANSWER_URL = "$BASE_URL/index/commentdeal"

    // The paid answer-reveal endpoints (payforshowanswer / showanswertrue) are deliberately absent:
    // the feature they backed was removed, and an endpoint constant with no caller is an invitation
    // to wire it up again. The HAR findings that documented them are recorded in git history.

    // Paid-hint flow: showtipsbuy returns a price quote (with separate normal/member/life-member
    // 学识 costs), showtips returns the actual hint text.
    const val SHOW_TIPS_URL = "$BASE_URL/index/showtips"
    const val SHOW_TIPS_BUY_URL = "$BASE_URL/index/showtipsbuy"

    // Praises ("点赞") a question. Confirmed live: form body `q_id=<id>&type=question`, replies
    // `{"status":"success","num":"<new upvote count>"}`.
    const val PRAISE_URL = "$BASE_URL/index/praise"

    // Daily check-in, captured from a logged-in web session (not the official Android app HAR):
    // POST /member/gettask with tasktype=lottery&reason=, then POST /index/signin with an empty
    // body. The capture only asserted HTTP 200, so response `status` strings are unconfirmed - see
    // DailyCheckIn. These are the website's own AJAX URLs; they do not take ACTION_QUERY_SUFFIX.
    const val DAILY_TASK_URL = "$BASE_URL/member/gettask"
    const val SIGN_IN_URL = "$BASE_URL/index/signin"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36 33iqNext/1.0"

    /** The page a browser renders for a question - what a share link must point at, not the JSON API URL. */
    fun questionPageUrl(id: Long): String = "$QUESTION_DETAIL_URL/$id.html"
}
