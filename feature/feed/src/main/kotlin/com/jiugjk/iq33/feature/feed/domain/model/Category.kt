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
                // 33IQ has no separate "featured" endpoint: 精选 is an ordinary tag like any other,
                // so these browse through the same /tag/<name>.html list, paging and refresh as the
                // rest. The Tab reads 精选题目 while the tag it filters on is plain 精选.
                Category("精选题目", "精选"),
                Category("恐怖推理", "恐怖推理"),
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
