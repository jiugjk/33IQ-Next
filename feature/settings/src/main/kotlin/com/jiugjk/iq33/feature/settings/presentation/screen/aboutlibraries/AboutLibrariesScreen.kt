package com.jiugjk.iq33.feature.settings.presentation.screen.aboutlibraries

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jiugjk.iq33.feature.settings.R
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * The open-source licence list, rendered entirely by AboutLibraries from build-time metadata.
 *
 * It has no state of its own, so it has no view model, action type or UI state: the removed ones
 * were a single constant `Content` state, an action type with no actions and a view model with no
 * behaviour, plus the injection and state collection needed to keep them wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutLibrariesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_libraries_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_libraries_screen_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LibrariesContainer(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

@Preview
@Composable
private fun AboutLibrariesScreenPreview() {
    AboutLibrariesScreen(
        onBackClick = { },
    )
}
