package com.jiugjk.iq33.feature.feed.domain.model

/**
 * Local answer / reveal history for one question under one account.
 *
 * Absence of a record means unknown — never treat it as a server-confirmed unanswered question.
 */
internal data class AnswerRecord(
    val questionId: Long,
    val accountKey: String,
    val title: String = "",
    val categoryLabel: String = "",
    val selectedOption: String? = null,
    val isCorrect: Boolean? = null,
    /** Known correct choice id/letter once revealed or answered correctly. */
    val correctOption: String? = null,
    val viewedExplanation: Boolean = false,
    val viewedHint: Boolean = false,
    /**
     * Text of content this account already paid for, cached so re-opening the question shows it
     * again without another charged request. Null means "entitled but not stored on this device"
     * (a record written before this cache existed), which is not the same as "never viewed".
     */
    val hintText: String? = null,
    val explanationText: String? = null,
    val knowledgeDelta: Int? = null,
    /** Non-null means this account has a local answered marker (detail may still be unknown). */
    val answeredAt: Long? = null,
    val updatedAt: Long = 0L,
) {
    /** Hide-filter: answered marker or viewed explanation. Hint alone does not hide. */
    val hidesFromFeed: Boolean
        get() =
            answeredAt != null ||
                isCorrect != null ||
                selectedOption != null ||
                viewedExplanation
}
