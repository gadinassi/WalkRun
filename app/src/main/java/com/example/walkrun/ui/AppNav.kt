package com.example.walkrun.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.walkrun.WalkRunApp
import com.example.walkrun.tracking.TrackingStore
import kotlinx.coroutines.flow.first
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.walkrun.report.WeeklyReportReceiver
import java.util.Locale

@Composable
fun AppNav(app: WalkRunApp) {
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        start = if (app.settings.account.first() == null) "login" else "home"
    }
    val startRoute = start
    if (startRoute == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()

    // After a workout is saved (from the screen or from the notification), open its summary.
    LaunchedEffect(tracking.lastSavedWorkoutId) {
        val id = tracking.lastSavedWorkoutId ?: return@LaunchedEffect
        TrackingStore.consumeSaved()
        nav.navigate("summary/$id") { popUpTo("home") }
    }
    val context = LocalContext.current
    LaunchedEffect(tracking.discardedMinKm) {
        val min = tracking.discardedMinKm ?: return@LaunchedEffect
        TrackingStore.consumeSaved()
        Toast.makeText(
            context,
            "האימון קצר מ-${String.format(Locale.US, "%.2f", min)} ק\"מ ולא נשמר בהיסטוריה.",
            Toast.LENGTH_LONG
        ).show()
        nav.popBackStack("home", inclusive = false)
    }
    // The weekly report alarm. Setting it again is harmless; it keeps it after an update.
    LaunchedEffect(Unit) { WeeklyReportReceiver.schedule(context) }

    NavHost(navController = nav, startDestination = startRoute) {
        composable("login") {
            LoginScreen(app, onSignedIn = {
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                app = app,
                onOpenWorkout = { nav.navigate("summary/$it") },
                onSettings = { nav.navigate("settings") },
                onRecording = { nav.navigate("record") { launchSingleTop = true } }
            )
        }
        composable("record") {
            RecordScreen(onBack = { nav.popBackStack() })
        }
        composable(
            "summary/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            SummaryScreen(
                app = app,
                id = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onRecording = { nav.navigate("record") { popUpTo("home") } }
            )
        }
        composable("settings") {
            SettingsScreen(
                app = app,
                onBack = { nav.popBackStack() },
                onSignedOut = {
                    nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                }
            )
        }
    }
}
