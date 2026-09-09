package com.jiugjk.iq33.feature.feed.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Category

@Composable
internal fun Category.label(): String {
    val resource =
        when (tagName) {
            "" -> R.string.feed_category_all
            "侦探推理" -> R.string.feed_category_detective
            "逻辑思维" -> R.string.feed_category_logic
            "谜语大全" -> R.string.feed_category_riddle
            "脑筋急转弯" -> R.string.feed_category_lateral
            "趣味益智" -> R.string.feed_category_fun
            "图形视觉" -> R.string.feed_category_visual
            "数学天地" -> R.string.feed_category_math
            "知识百科" -> R.string.feed_category_knowledge
            "决策判断" -> R.string.feed_category_decision
            "棋牌世界" -> R.string.feed_category_board
            "对联大全" -> R.string.feed_category_couplet
            else -> null
        }

    return if (resource != null) stringResource(resource) else displayName
}
