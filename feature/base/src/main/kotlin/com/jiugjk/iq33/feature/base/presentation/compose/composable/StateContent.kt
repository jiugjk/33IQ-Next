package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.R
import com.jiugjk.iq33.feature.base.common.res.Dimen

/*
 * The screen-level "nothing to show" states, written once here so every list in the app reports an
 * empty result, a failure and a retry the same way. Before this each screen rolled its own, and they
 * had drifted: different spacing, different button styles, some with no retry at all.
 */

/**
 * A screen with no content to show, for a reason that is not an error - an empty bookmark list, a
 * search that matched nothing.
 *
 * @param icon illustration for the empty reason.
 * @param title short headline.
 * @param modifier layout for the whole state.
 * @param description optional supporting text under the title.
 * @param actionLabel label for the optional call to action. Ignored when [action] is null.
 * @param action optional call to action. Omitted when there is nothing useful for the user to do
 *   here but navigate away.
 */
@Suppress("LongParameterList")
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
) {
    StateColumn(modifier = modifier) {
        Icon(
            imageVector = icon,
            // The title below says the same thing, so announcing the icon too would repeat it.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(StateIconSize),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimen.spaceL),
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimen.spaceM),
            )
        }

        if (action != null && actionLabel != null) {
            Button(onClick = action, modifier = Modifier.padding(top = Dimen.spaceXL)) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * A screen whose content failed to load, with the retry that goes with it.
 *
 * Distinct from [EmptyState] because the two mean opposite things to the user: empty is an answer,
 * an error is a request to try again.
 */
@Composable
fun ErrorState(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    StateColumn(modifier = modifier) {
        // The bare animation, not a separate captioned wrapper: ErrorState already has its own title.
        // would sit right above the title this state already shows. It sizes itself
        // (requiredSize), so passing a size here would be a no-op.
        LottieAssetLoader(assetResId = R.raw.lottie_error_screen)

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimen.spaceL),
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimen.spaceM),
            )
        }

        Button(onClick = onRetry, modifier = Modifier.padding(top = Dimen.spaceXL)) {
            Text(retryLabel)
        }
    }
}

@Composable
private fun StateColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Dimen.spaceXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private val StateIconSize = 48.dp

@Preview
@Composable
private fun ErrorStatePreview() {
    ErrorState(title = "加载失败", retryLabel = "重试", onRetry = { })
}
