package com.jiugjk.iq33.app.presentation

import kotlinx.serialization.Serializable

sealed interface NavigationRoute {
    @Serializable
    data object FeedList : NavigationRoute

    @Serializable
    data class QuestionDetail(
        val questionId: Long,
    ) : NavigationRoute

    @Serializable
    data object Search : NavigationRoute

    @Serializable
    data object Favourites : NavigationRoute

    @Serializable
    data object Settings : NavigationRoute

    @Serializable
    data object Login : NavigationRoute

    @Serializable
    data object AboutLibraries : NavigationRoute
}
