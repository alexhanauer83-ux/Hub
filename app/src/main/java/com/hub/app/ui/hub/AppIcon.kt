package com.hub.app.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * Lädt das Launcher-Icon einer App über den PackageManager und cached es pro Paketname.
 * Läuft in [remember], da `getApplicationIcon` vom System gecached und günstig ist; bei
 * Fehlern (App deinstalliert, kein Paketname) wird null geliefert und die aufrufende
 * Stelle zeigt den farbigen Quellen-Punkt als Rückfall.
 */
@Composable
fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName.isNullOrBlank()) return@remember null
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
}
