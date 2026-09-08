package com.jiugjk.iq33.feature.auth.presentation.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.auth.R
import com.jiugjk.iq33.feature.base.common.res.Dimen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onLoginSuccess: () -> Unit = {},
) {
    val viewModel: LoginViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            currentOnLoginSuccess()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.login_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )

        LoginForm(
            uiState = uiState,
            onLoginClick = viewModel::login,
        )
    }
}

@Composable
private fun LoginForm(
    uiState: LoginUiState,
    onLoginClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Dimen.spaceL),
    ) {
        Text(
            text = stringResource(R.string.login_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimen.spaceL),
        )

        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text(stringResource(R.string.login_account_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceM),
        )

        if (uiState is LoginUiState.Failure) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }

        Row(
            modifier = Modifier.padding(top = Dimen.spaceL),
            horizontalArrangement = Arrangement.End,
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = Dimen.spaceM).size(24.dp))
            }

            Button(
                onClick = { onLoginClick(account, password) },
                enabled = uiState !is LoginUiState.Loading,
            ) {
                Text(stringResource(R.string.login_submit))
            }
        }
    }
}

@Preview
@Composable
private fun LoginFormPreview() {
    LoginForm(uiState = LoginUiState.Idle, onLoginClick = { _, _ -> })
}
