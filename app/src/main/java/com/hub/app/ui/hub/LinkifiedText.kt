package com.hub.app.ui.hub

import android.util.Patterns
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Baut aus reinem Text einen [AnnotatedString], in dem enthaltene Web-Links anklickbar sind
 * (moderne Compose-`LinkAnnotation.Url`-API, ab Compose 1.7). Klick öffnet den Link im Browser;
 * funktioniert auch innerhalb eines `SelectionContainer` (Text bleibt auswählbar).
 */
@Composable
fun linkifiedText(raw: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(raw, linkColor) {
        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        )
        buildAnnotatedString {
            val matcher = Patterns.WEB_URL.matcher(raw)
            var last = 0
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                if (start > last) append(raw.substring(last, start))
                val shown = raw.substring(start, end)
                // Patterns.WEB_URL matcht auch schemalose Domains (www.x.de) -> https ergänzen.
                val url = if (shown.startsWith("http://", true) || shown.startsWith("https://", true)) {
                    shown
                } else {
                    "https://$shown"
                }
                withLink(LinkAnnotation.Url(url, styles)) { append(shown) }
                last = end
            }
            if (last < raw.length) append(raw.substring(last))
        }
    }
}
