package com.jiugjk.iq33.feature.feed.presentation.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import kotlinx.coroutines.delay

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val delayBeforeSubmittingQuery = 300L

    var textFieldValue by remember(query) { mutableStateOf(TextFieldValue(query)) }
    val focusRequester = remember { FocusRequester() }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearch by rememberUpdatedState(onSearch)

    LaunchedEffect(textFieldValue.text) {
        delay(delayBeforeSubmittingQuery)
        currentOnQueryChange(textFieldValue.text)
        currentOnSearch(textFieldValue.text)
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = textFieldValue,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Dimen.spaceM)
                .focusRequester(focusRequester),
        onValueChange = { newValue -> textFieldValue = newValue },
        placeholder = { Text(stringResource(R.string.feed_search_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon =
            if (textFieldValue.text.isNotEmpty()) {
                {
                    IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                    }
                }
            } else {
                null
            },
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Preview
@Composable
private fun SearchBarPreview() {
    SearchBar(query = "", onQueryChange = { }, onSearch = { })
}
