package com.jiugjk.iq33.app.presentation

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
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
        addOnDestinationChangedListener(navController)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BottomNavigationBar(navController) },
    ) { innerPadding ->

        val graph =
            navController.createGraph(startDestination = NavigationRoute.FeedList) {
                composable<NavigationRoute.FeedList> {
                    FeedListScreen(
                        onNavigateToQuestionDetail = { questionId ->
                            navController.navigate(NavigationRoute.QuestionDetail(questionId))
                        },
                        onNavigateToSearch = {
                            navController.navigate(NavigationRoute.Search)
                        },
                    )
                }
                composable<NavigationRoute.QuestionDetail> { backStackEntry ->
                    val args = backStackEntry.toRoute<NavigationRoute.QuestionDetail>()

                    QuestionDetailScreen(
                        questionId = args.questionId,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<NavigationRoute.Search> {
                    SearchScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavigateToQuestionDetail = { questionId ->
                            navController.navigate(NavigationRoute.QuestionDetail(questionId))
                        },
                    )
                }
                composable<NavigationRoute.Favourites> {
                    FavouriteScreen(
                        onQuestionClick = { questionId ->
                            navController.navigate(NavigationRoute.QuestionDetail(questionId))
                        },
                    )
                }
                composable<NavigationRoute.Settings> {
                    SettingsScreen(
                        onNavigateToAboutLibraries = {
                            navController.navigate(NavigationRoute.AboutLibraries)
                        },
                        onNavigateToLogin = {
                            navController.navigate(NavigationRoute.Login)
                        },
                    )
                }
                composable<NavigationRoute.Login> {
                    LoginScreen(
                        onBackClick = { navController.popBackStack() },
                        onLoginSuccess = { navController.popBackStack() },
                    )
                }
                composable<NavigationRoute.AboutLibraries> {
                    AboutLibrariesScreen(
                        onBackClick = {
                            navController.popBackStack()
                        },
                    )
                }
            }
        NavHost(
            navController = navController,
            graph = graph,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

private fun addOnDestinationChangedListener(navController: NavController) {
    navController.addOnDestinationChangedListener(
        object : NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?,
            ) {
                NavigationDestinationLogger.logDestinationChange(destination, arguments)
            }
        },
    )
}
