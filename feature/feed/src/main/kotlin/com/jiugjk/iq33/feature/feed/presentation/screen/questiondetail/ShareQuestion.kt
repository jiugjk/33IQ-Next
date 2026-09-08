package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import android.content.Context
import android.content.Intent
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

internal fun shareQuestion(
    context: Context,
    detail: QuestionDetail,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${detail.shortLabel}\n${detail.sourceUrl}")
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
