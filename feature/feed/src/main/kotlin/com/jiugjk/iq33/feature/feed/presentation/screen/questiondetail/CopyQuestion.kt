package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

/**
 * Renders the question as plain text: its body and every choice, and nothing else.
 *
 * Separate from [copyQuestion] so the formatting is testable without a [Context], and so it stays
 * the single definition of "the whole question" if another feature ever needs it.
 *
 * Neither the image URLs nor the question's own link are included. Copying is for pasting the
 * *question* somewhere - a chat, a note - and a trailing URL is noise there. Sharing is the action
 * that hands out a link, and it builds its own (see ShareQuestion).
 */
internal fun questionAsPlainText(detail: QuestionDetail): String =
    buildString {
        // Most 33IQ questions have no title at all (see QuestionDetail.title); the rare one that
        // does gets it as a first line rather than a repeat of the body.
        detail.title?.let { title ->
            appendLine(title)
            appendLine()
        }

        if (detail.bodyText.isNotBlank()) {
            appendLine(detail.bodyText)
        }

        if (detail.choices.isNotEmpty()) {
            appendLine()
            detail.choices.forEach { choice -> appendLine("${choice.id}. ${choice.text}") }
        }
    }.trimEnd()

/**
 * Copies [detail] to the clipboard as plain text.
 *
 * Android 13 shows its own confirmation for every copy, so the app only raises a toast on older
 * versions - on newer ones it would sit on top of the system's own popup saying the same thing.
 */
internal fun copyQuestion(
    context: Context,
    detail: QuestionDetail,
) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return

    clipboard.setPrimaryClip(ClipData.newPlainText(detail.shortLabel, questionAsPlainText(detail)))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.feed_copy_done, Toast.LENGTH_SHORT).show()
    }
}
