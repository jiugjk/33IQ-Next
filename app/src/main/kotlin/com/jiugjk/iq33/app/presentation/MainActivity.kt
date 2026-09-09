package com.jiugjk.iq33.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.presentation.compose.theme.Iq33Theme
import com.jiugjk.iq33.feature.settings.domain.model.ThemeMode
import com.jiugjk.iq33.feature.settings.domain.usecase.ObserveThemeModeUseCase
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val darkTheme = rememberDarkTheme()

            // enableEdgeToEdge picks its bar icon colours once, from the system's dark mode. The
            // app's own light/dark/system preference can disagree with that, so the bars are re-set
            // whenever the resolved theme changes - otherwise dark icons end up on a dark bar for
            // anyone who forces a theme against their system setting.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme),
                )
            }

            Iq33Theme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }

    /** Resolves the stored light/dark/system preference against the current system setting. */
    @Composable
    private fun rememberDarkTheme(): Boolean {
        val observeThemeModeUseCase: ObserveThemeModeUseCase = koinInject()
        val themeMode by observeThemeModeUseCase().collectAsStateWithLifecycle()
        val systemDarkTheme = isSystemInDarkTheme()

        return when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDarkTheme
        }
    }

    /**
     * A transparent bar whose icons are chosen for [darkTheme].
     *
     * `SystemBarStyle.auto` decides light/dark from the mask it is given rather than from the system
     * setting, which is what lets the bars follow the app's own theme preference.
     */
    private fun systemBarStyle(darkTheme: Boolean) =
        if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
}
