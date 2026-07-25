package com.hub.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hub.app.ui.hub.HubScreen

@Composable
fun HubNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destinations.HUB) {
        composable(Destinations.HUB) { HubScreen() }
        // Weitere Routen (Priority Hub, Archiv, Settings, Onboarding, App-Lock)
        // werden in den folgenden Phasen ergänzt.
    }
}
