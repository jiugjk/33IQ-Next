package com.jiugjk.iq33.app.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jiugjk.iq33.app.R

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navigationItems = getBottomNavigationItems()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val selectedNavigationIndex = getSelectedNavigationIndex(currentRoute, navigationItems)

    NavigationBar(
        modifier = modifier,
    ) {
        navigationItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedNavigationIndex == index,
                onClick = {
                    navController.navigate(item.route) {
                        // Multiple back stacks: saveState is what restoreState restores from - without
                        // it each tab was rebuilt from scratch, losing its category, paging and scroll
                        // position. Popping to the graph's own start destination (rather than id 0)
                        // keeps a single, well-defined back stack behind the tabs.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                label = {
                    Text(
                        stringResource(item.titleRes),
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.surface,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

private fun getBottomNavigationItems() =
    listOf(
        NavigationBarItem(
            R.string.bottom_navigation_feed,
            Icons.Default.Home,
            NavigationRoute.FeedList,
        ),
        NavigationBarItem(
            R.string.bottom_navigation_favorites,
            Icons.Default.Bookmark,
            NavigationRoute.Favourites,
        ),
        NavigationBarItem(
            R.string.bottom_navigation_settings,
            Icons.Default.Person,
            NavigationRoute.Settings,
        ),
    )

/*
Returns the index of the selected bottom menu item based on the current route.
If no match is found, it defaults to the first item (index 0).
*/
private fun getSelectedNavigationIndex(
    currentRoute: String?,
    navigationItems: List<NavigationBarItem>,
): Int =
    navigationItems
        .indexOfFirst { item ->
            when (currentRoute) {
                null -> false
                NavigationRoute.QuestionDetail::class.qualifiedName -> item.route is NavigationRoute.FeedList
                NavigationRoute.Search::class.qualifiedName -> item.route is NavigationRoute.FeedList
                NavigationRoute.Login::class.qualifiedName -> item.route is NavigationRoute.Settings
                NavigationRoute.AboutLibraries::class.qualifiedName -> item.route is NavigationRoute.Settings
                else -> item.route::class.qualifiedName == currentRoute
            }
        }.takeIf { it >= 0 } ?: 0

data class NavigationBarItem(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: NavigationRoute,
)

@Preview
@Composable
private fun BottomNavigationBarPreview() {
    BottomNavigationBar(
        navController = rememberNavController(),
    )
}
