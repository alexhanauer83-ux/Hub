package com.hub.app.sms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Pflichtkomponente für die Standard-SMS-App-Rolle: Android verlangt eine Activity, die
 * `ACTION_SENDTO` mit den Schemata sms/smsto/mms/mmsto verarbeitet – das ist der
 * "Neue SMS schreiben"-Einstieg aus anderen Apps.
 *
 * Hub ist kein vollwertiger SMS-Client, sondern ein Aggregator. Statt einen halbgaren
 * eigenen Composer zu bauen, leiten wir hier bewusst in den Hub-Feed weiter. Ein
 * eigenständiger Composer wäre der nächste sinnvolle Ausbauschritt, wenn Hub die
 * SMS-Rolle dauerhaft übernehmen soll.
 */
class ComposeSmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, com.hub.app.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
