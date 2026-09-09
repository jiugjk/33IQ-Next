package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import android.content.Context
import android.content.Intent
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.library.network.ShareConfig

/**
 * Hands [detail] to the system share sheet as a label plus its 33IQ link.
 *
 * The link is built from the question's id through [ShareConfig], not taken from
 * [QuestionDetail.sourceUrl]: every link this app shares has to carry the same tracking parameters,
 * and building them in one place is what keeps that true as share entry points are added.
 */
internal fun shareQuestion(
    context: Context,
    detail: QuestionDetail,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${detail.shortLabel}\n${ShareConfig.questionShareUrl(detail.id)}")
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
