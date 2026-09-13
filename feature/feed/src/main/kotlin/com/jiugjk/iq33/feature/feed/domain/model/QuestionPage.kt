package com.jiugjk.iq33.feature.feed.domain.model

/** The next URL comes from the server's pagination links, never from a guessed parameter. */
internal data class QuestionPage(
    val questions: List<QuestionSummary>,
    val nextPageUrl: String?,
)
