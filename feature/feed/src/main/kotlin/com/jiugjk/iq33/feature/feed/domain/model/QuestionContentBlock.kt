package com.jiugjk.iq33.feature.feed.domain.model

/** One piece of a question stem, in document order. */
sealed interface QuestionContentBlock {
    data class Text(
        val text: String,
    ) : QuestionContentBlock

    data class Image(
        val url: String,
    ) : QuestionContentBlock
}
