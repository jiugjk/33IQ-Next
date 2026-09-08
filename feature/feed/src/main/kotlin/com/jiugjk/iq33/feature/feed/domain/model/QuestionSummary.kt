package com.jiugjk.iq33.feature.feed.domain.model

data class QuestionSummary(
    val id: Long,
    val title: String,
    val tags: List<String>,
    val upvoteCount: Int,
    val commentCount: Int,
)
