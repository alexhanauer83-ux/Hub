package com.hub.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hub.app.security.AppLockManager
import com.hub.app.ui.lock.AppLockScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hub.app.ui.navigation.HubNavGraph
import com.hub.app.ui.theme.HubTheme
import com.hub.app.ui.theme.ThemeSettings

/**
 * [FragmentActivity] statt ComponentActivity, weil BiometricPrompt (App-Lock) sich
 * intern an den Fragment-Manager hängt.
 */
class MainActivity : FragmentActivity() {

    private lateinit var appLock: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLock = AppLockManager(this)
        // Gespeicherten Darstellungsmodus in den geteilten Flow laden.
        ThemeSettings(this).sync()

        setContent {
            val themeMode by ThemeSettings.mode.collectAsStateWithLifecycle()
            HubTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var locked by remember { mutableStateOf(appLock.requiresUnlock()) }

                    // Beim Wechsel in den Hintergrund sperren, beim Zurueckkehren erneut
                    // pruefen. Muss in der Composition sitzen, damit die Sperre den Screen
                    // tatsaechlich wieder einblendet - onCreate laeuft dabei nicht erneut.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> appLock.lock()
                                Lifecycle.Event.ON_START -> locked = appLock.requiresUnlock()
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (locked) {
                        AppLockScreen(
                            onUnlocked = {
                                appLock.onUnlockSucceeded()
                                locked = false
                            }
                        )
                    } else {
                        HubNavGraph()
                    }
                }
            }
        }
    }
}
