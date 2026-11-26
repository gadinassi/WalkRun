package com.example.walkrun

import android.content.Intent
import android.graphics.Color
import com.example.walkrun.report.WalkRunWidget
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.data.DisplaySettings
import com.example.walkrun.ui.AppNav
import com.example.walkrun.ui.LocalDarkTheme
import com.example.walkrun.ui.WalkRunTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the system bars. The bar icons follow the app theme (set again below).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as WalkRunApp
        takeStartRequest(intent)
        setContent {
            val display by app.settings.display.collectAsStateWithLifecycle(initialValue = DisplaySettings())
            WalkRunTheme(display.theme) {
                val dark = LocalDarkTheme.current
                // Light icons on the dark theme, dark icons on the light theme.
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        AppNav(app)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeStartRequest(intent)
    }

    /** The ▶ of the home screen widget: start a workout once the app is shown. */
    private fun takeStartRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(WalkRunWidget.EXTRA_START, false) == true) {
            intent.removeExtra(WalkRunWidget.EXTRA_START)
            StartRequest.pending.value = true
        }
    }
}

/** A start request from outside the app (the widget). AppNav starts the workout and clears it. */
object StartRequest {
    val pending = MutableStateFlow(false)
}
