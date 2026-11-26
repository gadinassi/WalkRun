package com.example.walkrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.walkrun.ui.AppNav
import com.example.walkrun.ui.WalkRunTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the system bars and picks dark or light bar icons that match the background.
        // Without it, the status bar icons are white on the light app background.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as WalkRunApp
        setContent {
            WalkRunTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        AppNav(app)
                    }
                }
            }
        }
    }
}
