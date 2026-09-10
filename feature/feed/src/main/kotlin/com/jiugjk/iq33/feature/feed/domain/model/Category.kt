package com.jiugjk.iq33.feature.feed.domain.model

/**
 * Where a browse category's question list actually lives on 33IQ.
 *
 * Display name, Compose identity and the request path used to be the same `tagName` string, which
 * sent 精选题目 to a non-existent `/tag/精选.html` and 恐怖推理 to `/tag/恐怖推理.html` instead of
 * the real nested tag. Those three roles are split here so a chip label can stay short while the
 * request uses the site's own path.
 */
sealed interface CategorySource {
    /** The site's default question list at `/question/` (精选题目, and "全部" until a distinct sort is confirmed). */
    data object QuestionList : CategorySource

    /** A `/tag/<path>.html` list. [path] is the site's own tag slug, which may include a parent prefix. */
    data class Tag(
        val path: String,
    ) : CategorySource
}

/** Top-level tags surfaced on 33IQ's homepage, used as browsing categories. */
data class Category(
    val id: String,
    val displayName: String,
    val source: CategorySource,
) {
    companion object {
        val ALL = Category(id = "all", displayName = "全部", source = CategorySource.QuestionList)

        val FEATURED = Category(id = "featured", displayName = "精选题目", source = CategorySource.QuestionList)

        val DEFAULT_CATEGORIES =
            listOf(
                ALL,
                FEATURED,
                Category("horror", "恐怖推理", CategorySource.Tag("侦探推理-恐怖推理")),
                Category("detective", "侦探推理", CategorySource.Tag("侦探推理")),
                Category("logic", "逻辑思维", CategorySource.Tag("逻辑思维")),
                Category("riddle", "谜语大全", CategorySource.Tag("谜语大全")),
                Category("lateral", "脑筋急转弯", CategorySource.Tag("脑筋急转弯")),
                Category("fun", "趣味益智", CategorySource.Tag("趣味益智")),
                Category("visual", "图形视觉", CategorySource.Tag("图形视觉")),
                Category("math", "数学天地", CategorySource.Tag("数学天地")),
                Category("knowledge", "知识百科", CategorySource.Tag("知识百科")),
                Category("decision", "决策判断", CategorySource.Tag("决策判断")),
                Category("board", "棋牌世界", CategorySource.Tag("棋牌世界")),
                Category("couplet", "对联大全", CategorySource.Tag("对联大全")),
            )
    }
}

/** Tag text that every card in this category already carries, so the chip row can hide it. */
fun Category.redundantCardTag(): String? =
    when (val source = source) {
        CategorySource.QuestionList -> null
        is CategorySource.Tag -> source.path.substringAfterLast('-')
    }
