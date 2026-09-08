package com.jiugjk.iq33.feature.feed.domain.model

enum class QuestionType {
    CHOICE,
    OPEN,
}

data class Choice(
    val id: String,
    val text: String,
)

data class Comment(
    val author: String,
    val content: String,
    val time: String,
)

data class QuestionDetail(
    val id: Long,
    val title: String,
    val tags: List<String>,
    val breadcrumb: List<String>,
    val author: String?,
    val publishedDate: String?,
    val upvoteCount: Int,
    val commentCount: Int,
    /** Number of users who have 收藏'd (bookmarked on 33IQ's own servers) this question. */
    val collectCount: Int,
    /** Percentage of answerers who got this right, 0-100, when 33IQ reports one. */
    val rightRatio: Int?,
    val questionType: QuestionType,
    val choices: List<Choice>,
    /** Answer/analysis text, when it could be found without login (33IQ hides this for most guests). */
    val analysis: String?,
    val comments: List<Comment>,
    val sourceUrl: String,
)
