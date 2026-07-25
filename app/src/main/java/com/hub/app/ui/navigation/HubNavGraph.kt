package com.hub.app.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hub.app.notification.NotificationAccess
import com.hub.app.ui.hub.HubScreen
import com.hub.app.ui.onboarding.OnboardingScreen

@Composable
fun HubNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    // Der Hub ist auch ohne Berechtigung nutzbar (z. B. für API-Connectoren), daher startet
    // die App nur beim allerersten Mal im Onboarding.
    val start = if (NotificationAccess.isGranted(context)) Destinations.HUB else Destinations.ONBOARDING

    NavHost(navController = navController, startDestination = start) {
        composable(Destinations.ONBOARDING) {
            val toHub: () -> Unit = {
                navController.navigate(Destinations.HUB) {
                    popUpTo(Destinations.ONBOARDING) { inclusive = true }
                }
            }
            // Nach Rückkehr aus den Systemeinstellungen automatisch weiterspringen,
            // sobald die Berechtigung erteilt wurde.
            LifecycleResumeEffect(Unit) {
                if (NotificationAccess.isGranted(context)) toHub()
                onPauseOrDispose { }
            }
            OnboardingScreen(
                onGrantAccess = { openNotificationSettings(context) },
                onSkip = toHub
            )
        }
        composable(Destinations.HUB) {
            HubScreen(onOpenOnboarding = { navController.navigate(Destinations.ONBOARDING) })
        }
        // Priority Hub, Archiv und Einstellungen folgen in Phase 3 bzw. 7.
    }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(NotificationAccess.settingsIntent(context))
}
