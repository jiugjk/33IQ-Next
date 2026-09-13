package com.jiugjk.iq33.feature.feed.domain.model

/** A question batch and its continuation, either advertised by the site or using legacy paging. */
internal data class QuestionPage(
    val questions: List<QuestionSummary>,
    val nextPageUrl: String?,
    /** Stop a duplicate-only response immediately when continuing via the legacy page protocol. */
    val isNextPageInferred: Boolean = false,
)
