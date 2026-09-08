package com.jiugjk.iq33.app.presentation

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BottomNavigationBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            graph = navGraph,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

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
