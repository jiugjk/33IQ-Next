package com.jiugjk.iq33.feature.feed.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Category

/**
 * 33IQ's own category names, mapped to their translated labels.
 *
 * A category 33IQ adds later simply falls through to [Category.displayName] - its Chinese name,
 * which is still better than hiding the category altogether.
 */
private val CATEGORY_LABELS: Map<String, Int> =
    mapOf(
        "all" to R.string.feed_category_all,
        "featured" to R.string.feed_category_featured,
        "horror" to R.string.feed_category_horror,
        "detective" to R.string.feed_category_detective,
        "logic" to R.string.feed_category_logic,
        "riddle" to R.string.feed_category_riddle,
        "lateral" to R.string.feed_category_lateral,
        "fun" to R.string.feed_category_fun,
        "visual" to R.string.feed_category_visual,
        "math" to R.string.feed_category_math,
        "knowledge" to R.string.feed_category_knowledge,
        "decision" to R.string.feed_category_decision,
        "board" to R.string.feed_category_board,
        "couplet" to R.string.feed_category_couplet,
    )

@Composable
internal fun Category.label(): String {
    val resource = CATEGORY_LABELS[id]

    return if (resource != null) stringResource(resource) else displayName
}
