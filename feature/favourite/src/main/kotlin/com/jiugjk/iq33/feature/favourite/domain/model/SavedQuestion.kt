package com.jiugjk.iq33.feature.favourite.domain.model

data class SavedQuestion(
    val id: Long,
    val title: String,
    val tags: List<String>,
    val savedAt: Long,
)
