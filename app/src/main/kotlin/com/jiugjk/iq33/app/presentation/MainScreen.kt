package com.jiugjk.iq33.app.presentation

import android.os.Bundle
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import com.jiugjk.iq33.app.BuildConfig
import com.jiugjk.iq33.app.presentation.util.NavigationDestinationLogger
import com.jiugjk.iq33.feature.auth.presentation.screen.login.LoginScreen
import com.jiugjk.iq33.feature.favourite.presentation.screen.favourite.FavouriteScreen
import com.jiugjk.iq33.feature.feed.presentation.screen.feedlist.FeedListScreen
import com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail.QuestionDetailScreen
import com.jiugjk.iq33.feature.feed.presentation.screen.search.SearchScreen
import com.jiugjk.iq33.feature.settings.presentation.screen.aboutlibraries.AboutLibrariesScreen
import com.jiugjk.iq33.feature.settings.presentation.screen.settings.SettingsScreen

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    if (BuildConfig.DEBUG) {
        // DisposableEffect, not a bare call in the composable body: the body runs again on every
        // recomposition, which added another listener each time and never removed any of them.
        DisposableEffect(navController) {
            val listener = destinationLoggingListener()
            navController.addOnDestinationChangedListener(listener)

            onDispose { navController.removeOnDestinationChangedListener(listener) }
        }
    }

    // The graph is a plain object graph, not composition state - rebuilding it on every
    // recomposition is pure work with no effect on what is displayed.
    val navGraph = remember(navController) { navController.buildAppNavGraph() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = navBackStackEntry?.destination?.showsBottomBar() == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            graph = navGraph,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInForward() },
            exitTransition = { slideOutForward() },
            popEnterTransition = { slideInBack() },
            popExitTransition = { slideOutBack() },
        )
    }
}

/*
 * Page transitions.
 *
 * Forward navigation still slides by a fraction of the width: the motion stays quick and the
 * outgoing screen never fully clears the frame - restrained, per the design brief.
 *
 * Back is different. Navigation Compose seeks [popExitTransition] / [popEnterTransition] against
 * the predictive-back gesture, so the outgoing screen has to travel the full width or the image
 * lags behind the finger. The incoming screen keeps the smaller parallax drift.
 *
 * These are explicit specs rather than MaterialTheme.motionScheme: material3 1.4.0 compiles the
 * Expressive motion API internal, so it cannot be read here yet. See Iq33Theme.
 */
private const val SLIDE_FRACTION = 4
private const val TRANSITION_MILLIS = 300

private fun slideInForward(): EnterTransition =
    slideInHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { width -> width / SLIDE_FRACTION } +
        fadeIn(animationSpec = tween(TRANSITION_MILLIS))

private fun slideOutForward(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { width -> -width / SLIDE_FRACTION } +
        fadeOut(animationSpec = tween(TRANSITION_MILLIS))

private fun slideInBack(): EnterTransition =
    slideInHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { width -> -width / SLIDE_FRACTION } +
        fadeIn(animationSpec = tween(TRANSITION_MILLIS))

private fun slideOutBack(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { width -> width } +
        fadeOut(animationSpec = tween(TRANSITION_MILLIS))

private fun NavController.buildAppNavGraph(): NavGraph =
    createGraph(startDestination = NavigationRoute.FeedList) {
        composable<NavigationRoute.FeedList> {
            FeedListScreen(
                onNavigateToQuestionDetail = { questionId ->
                    navigate(NavigationRoute.QuestionDetail(questionId))
                },
                onNavigateToSearch = {
                    navigate(NavigationRoute.Search)
                },
            )
        }
        composable<NavigationRoute.QuestionDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<NavigationRoute.QuestionDetail>()

            QuestionDetailScreen(
                questionId = args.questionId,
                onBackClick = { popBackStack() },
            )
        }
        composable<NavigationRoute.Search> {
            SearchScreen(
                onBackClick = { popBackStack() },
                onNavigateToQuestionDetail = { questionId ->
                    navigate(NavigationRoute.QuestionDetail(questionId))
                },
            )
        }
        composable<NavigationRoute.Favourites> {
            FavouriteScreen(
                onQuestionClick = { questionId ->
                    navigate(NavigationRoute.QuestionDetail(questionId))
                },
            )
        }
        composable<NavigationRoute.Settings> {
            SettingsScreen(
                onNavigateToAboutLibraries = {
                    navigate(NavigationRoute.AboutLibraries)
                },
                onNavigateToLogin = {
                    navigate(NavigationRoute.Login)
                },
            )
        }
        composable<NavigationRoute.Login> {
            LoginScreen(
                onBackClick = { popBackStack() },
                onLoginSuccess = { popBackStack() },
            )
        }
        composable<NavigationRoute.AboutLibraries> {
            AboutLibrariesScreen(
                onBackClick = { popBackStack() },
            )
        }
    }

private fun destinationLoggingListener() =
    NavController.OnDestinationChangedListener { _: NavController, destination: NavDestination, arguments: Bundle? ->
        NavigationDestinationLogger.logDestinationChange(destination, arguments)
    }
