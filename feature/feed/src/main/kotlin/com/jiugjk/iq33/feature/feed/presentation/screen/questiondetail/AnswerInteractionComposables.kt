package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal

@Composable
internal fun HintSection(
    hintReveal: RevealState<HintQuote, HintReveal>,
    canStartHintReveal: Boolean,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        if (hintReveal is RevealState.Revealed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = hintReveal.reveal.tips,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Dimen.spaceL),
                )
            }
        } else {
            OutlinedButton(
                onClick = { onEvent(QuestionDetailEvent.HintQuoteRequested) },
                enabled = canStartHintReveal,
            ) {
                Text(stringResource(R.string.feed_reveal_hint_button))
            }
        }

        if (hintReveal is RevealState.Failed) {
            Text(
                text =
                    stringResource(
                        if (hintReveal.afterSideEffect) R.string.feed_flow_failed_after_side_effect else R.string.feed_flow_failed,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }
    }

    if (hintReveal is RevealState.QuoteReady) {
        HintQuoteDialog(
            quote = hintReveal.quote,
            onConfirm = { onEvent(QuestionDetailEvent.HintRevealConfirmed) },
            onDismiss = { onEvent(QuestionDetailEvent.HintFlowDismissed) },
        )
    }
}

@Composable
private fun HintQuoteDialog(
    quote: HintQuote,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_reveal_hint_confirm_title)) },
        text = {
            // Only the effective cost is guaranteed; 33IQ may omit a membership tier, and inventing
            // a 0 for a missing tier would advertise a free hint that does not exist.
            val tiers = listOfNotNull(quote.normalCost, quote.memberCost, quote.lifeMemberCost)
            val message =
                if (tiers.size == ALL_MEMBERSHIP_TIERS) {
                    stringResource(
                        R.string.feed_reveal_hint_confirm_message,
                        quote.normalCost ?: 0,
                        quote.memberCost ?: 0,
                        quote.lifeMemberCost ?: 0,
                        quote.effectiveCost,
                    )
                } else {
                    stringResource(R.string.feed_reveal_hint_confirm_message_simple, quote.effectiveCost)
                }

            Text(message)
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) } },
    )
}

/** 普通 / 会员 / 终身会员 - the three tiers `showtipsbuy` quotes. */
private const val ALL_MEMBERSHIP_TIERS = 3
