package com.hub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hub.app.ui.navigation.HubNavGraph
import com.hub.app.ui.theme.HubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HubNavGraph()
                }
            }
        }
    }
}
