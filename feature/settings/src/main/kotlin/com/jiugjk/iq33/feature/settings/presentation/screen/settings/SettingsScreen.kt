package com.jiugjk.iq33.feature.settings.presentation.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.settings.R
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.library.network.IqSession
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAboutLibraries: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToLogin: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        when (val currentUiState = uiState) {
            is SettingsUiState.Content ->
                SettingsContent(
                    uiState = currentUiState,
                    onNavigateToAboutLibraries = onNavigateToAboutLibraries,
                    onNavigateToLogin = onNavigateToLogin,
                    onThemeModeSelect = viewModel::onThemeModeSelected,
                    onLogoutClick = viewModel::onLogoutClick,
                )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState.Content,
    onNavigateToAboutLibraries: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        AccountCard(session = uiState.session, onNavigateToLogin = onNavigateToLogin, onLogoutClick = onLogoutClick)

        ThemeCard(themeMode = uiState.themeMode, onThemeModeSelect = onThemeModeSelect)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceM),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SettingsItem(
                title = stringResource(R.string.settings_screen_open_source_licenses),
                subtitle = stringResource(R.string.settings_screen_view_licenses_description),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.settings_screen_licenses),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onNavigateToAboutLibraries,
            )
        }

        Text(
            text = stringResource(R.string.settings_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimen.spaceL),
        )
    }
}

@Composable
private fun AccountCard(
    session: IqSession,
    onNavigateToLogin: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimen.spaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.padding(end = Dimen.spaceM),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(modifier = Modifier.weight(1f)) {
                if (session.isLoggedIn) {
                    Text(stringResource(R.string.settings_logged_in), style = MaterialTheme.typography.bodyLarge)
                    val score = session.score
                    if (score != null) {
                        Text(
                            text = stringResource(R.string.settings_score, score),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(stringResource(R.string.settings_not_logged_in), style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (session.isLoggedIn) {
                TextButton(onClick = onLogoutClick) {
                    Text(stringResource(R.string.settings_logout))
                }
            } else {
                Button(onClick = onNavigateToLogin) {
                    Text(stringResource(R.string.settings_login))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeCard(
    themeMode: ThemeMode,
    onThemeModeSelect: (ThemeMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceM),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(text = stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceM)) {
                val options =
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    )

                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { onThemeModeSelect(mode) },
                        selected = themeMode == mode,
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        leadingContent = icon,
        trailingContent =
            if (enabled) {
                {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.settings_screen_navigate),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SettingsContent(
        uiState = SettingsUiState.Content(),
        onNavigateToAboutLibraries = { },
        onNavigateToLogin = { },
        onThemeModeSelect = { },
        onLogoutClick = { },
    )
}
