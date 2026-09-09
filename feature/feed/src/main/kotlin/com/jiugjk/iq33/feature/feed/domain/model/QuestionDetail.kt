package com.jiugjk.iq33.feature.feed.domain.model

enum class QuestionType {
    CHOICE,
    OPEN,
}

data class Choice(
    val id: String,
    val text: String,
)

data class QuestionDetail(
    val id: Long,
    /**
     * 33IQ's own `qc_title`, which is **null for ordinary questions**: the site has no title concept
     * for them - a question is just its body - and serves `qc_title` as an empty string. Its own
     * pages simply print a truncation of the body wherever a one-line label is needed.
     *
     * So this is not a heading the UI can rely on. Anything that needs one line of text should use
     * [shortLabel]; the question itself is [bodyText].
     */
    val title: String?,
    /**
     * The question's full body text (33IQ's `qc_context`), converted from its rich-text HTML. This
     * is the question as asked, and it is never truncated.
     */
    val bodyText: String,
    /** Images embedded in the body, in document order - some questions are answerable only from these. */
    val imageUrls: List<String>,
    val tags: List<String>,
    val breadcrumb: List<String>,
    val author: String?,
    val publishedDate: String?,
    val upvoteCount: Int,
    val isUpvoted: Boolean,
    /**
     * How many comments 33IQ reports on this question. Shown as a stat only - the app does not
     * list comments (33IQ exposes no endpoint for them), so this is a popularity signal, not a
     * link to content.
     */
    val commentCount: Int,
    /** Number of users who have 收藏'd (bookmarked on 33IQ's own servers) this question. */
    val collectCount: Int,
    /** Percentage of answerers who got this right, 0-100, when 33IQ reports one. */
    val rightRatio: Int?,
    val questionType: QuestionType,
    val choices: List<Choice>,
    /** Answer/analysis text, when it could be found without login (33IQ hides this for most guests). */
    val analysis: String?,
    /** The public, browser-readable page for this question - what sharing must hand out. */
    val sourceUrl: String,
) {
    /**
     * One line of text identifying this question, for the places that genuinely need a label rather
     * than the question itself: a bookmark row, a share sheet's subject.
     *
     * Falls back to a truncated body the same way 33IQ's own list pages do. It is deliberately *not*
     * used as a heading above [bodyText] - that showed every question twice, once cut off.
     */
    val shortLabel: String
        get() = title ?: bodyText.take(SHORT_LABEL_MAX_LENGTH)

    private companion object {
        const val SHORT_LABEL_MAX_LENGTH = 60
    }
}
