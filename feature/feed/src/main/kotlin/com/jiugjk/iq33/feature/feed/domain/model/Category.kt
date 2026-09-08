package com.jiugjk.iq33.feature.feed.domain.model

/** Top-level tags surfaced on 33IQ's homepage, used as browsing categories. */
data class Category(
    val displayName: String,
    val tagName: String,
) {
    companion object {
        val ALL = Category(displayName = "全部", tagName = "")

        val DEFAULT_CATEGORIES =
            listOf(
                ALL,
                Category("侦探推理", "侦探推理"),
                Category("逻辑思维", "逻辑思维"),
                Category("谜语大全", "谜语大全"),
                Category("脑筋急转弯", "脑筋急转弯"),
                Category("趣味益智", "趣味益智"),
                Category("图形视觉", "图形视觉"),
                Category("数学天地", "数学天地"),
                Category("知识百科", "知识百科"),
                Category("决策判断", "决策判断"),
                Category("棋牌世界", "棋牌世界"),
                Category("对联大全", "对联大全"),
            )
    }
}
