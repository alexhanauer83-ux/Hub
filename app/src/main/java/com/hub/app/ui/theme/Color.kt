package com.hub.app.ui.theme

import androidx.compose.ui.graphics.Color

// Eigener, reduzierter Hub-Stil (kein 1:1-BlackBerry-Look): dunkler Grundton,
// ein einzelner Akzentton (Mint) für Priorität/Unread statt bunter Iconography.
val HubBackground = Color(0xFF0E0F13)
val HubSurface = Color(0xFF17191F)
val HubSurfaceVariant = Color(0xFF1F222A)
val HubOnSurface = Color(0xFFE7E9EE)
val HubOnSurfaceMuted = Color(0xFF9AA0AC)
val HubAccent = Color(0xFF6FE3C4)
val HubAccentMuted = Color(0xFF2E4A44)
val HubDanger = Color(0xFFE36F7A)
val HubOutline = Color(0xFF2B2E37)

// Helles Theme als Fallback (Dark ist Standard, siehe Theme.kt)
val HubLightBackground = Color(0xFFFAFAFC)
val HubLightSurface = Color(0xFFFFFFFF)
val HubLightOnSurface = Color(0xFF14161B)
