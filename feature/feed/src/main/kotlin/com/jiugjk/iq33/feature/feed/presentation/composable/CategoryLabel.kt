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
        "" to R.string.feed_category_all,
        "精选" to R.string.feed_category_featured,
        "恐怖推理" to R.string.feed_category_horror,
        "侦探推理" to R.string.feed_category_detective,
        "逻辑思维" to R.string.feed_category_logic,
        "谜语大全" to R.string.feed_category_riddle,
        "脑筋急转弯" to R.string.feed_category_lateral,
        "趣味益智" to R.string.feed_category_fun,
        "图形视觉" to R.string.feed_category_visual,
        "数学天地" to R.string.feed_category_math,
        "知识百科" to R.string.feed_category_knowledge,
        "决策判断" to R.string.feed_category_decision,
        "棋牌世界" to R.string.feed_category_board,
        "对联大全" to R.string.feed_category_couplet,
    )

@Composable
internal fun Category.label(): String {
    val resource = CATEGORY_LABELS[tagName]

    return if (resource != null) stringResource(resource) else displayName
}
