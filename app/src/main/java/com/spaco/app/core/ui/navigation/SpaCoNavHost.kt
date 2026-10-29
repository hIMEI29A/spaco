package com.spaco.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spaco.app.feature.decks.DeckDetailRoute
import com.spaco.app.feature.decks.DeckDetailViewModel
import com.spaco.app.feature.decks.DeckListRoute
import com.spaco.app.feature.progress.ProgressRoute
import com.spaco.app.feature.session.SessionRoute
import com.spaco.app.feature.settings.SettingsRoute

/** Route table for the single-activity app. */
object SpaCoRoutes {
    const val DECKS = "decks"
    const val DECK_DETAIL = "deck/{deckId}"
    const val SESSION = "session/{deckId}"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"

    fun deckDetail(deckId: String) = "deck/$deckId"
    fun session(deckId: String) = "session/$deckId"
}

@Composable
fun SpaCoNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = SpaCoRoutes.DECKS) {
        composable(SpaCoRoutes.DECKS) {
            DeckListRoute(
                onOpenDeck = { deckId -> navController.navigate(SpaCoRoutes.deckDetail(deckId)) },
                onStartPractice = { deckId -> navController.navigate(SpaCoRoutes.session(deckId)) },
                onOpenProgress = { navController.navigate(SpaCoRoutes.PROGRESS) },
                onOpenSettings = { navController.navigate(SpaCoRoutes.SETTINGS) },
            )
        }
        composable(
            route = SpaCoRoutes.DECK_DETAIL,
            arguments = listOf(navArgument(DeckDetailViewModel.DECK_ID_ARG) { type = NavType.StringType }),
        ) {
            DeckDetailRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = SpaCoRoutes.SESSION,
            arguments = listOf(navArgument(SESSION_DECK_ID) { type = NavType.StringType }),
        ) {
            SessionRoute(onFinished = { navController.popBackStack() })
        }
        composable(SpaCoRoutes.PROGRESS) {
            ProgressRoute()
        }
        composable(SpaCoRoutes.SETTINGS) {
            SettingsRoute()
        }
    }
}

const val SESSION_DECK_ID = "deckId"
