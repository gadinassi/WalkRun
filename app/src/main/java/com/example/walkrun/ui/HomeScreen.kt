package com.example.walkrun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import com.example.walkrun.data.ReportSettings
import com.example.walkrun.data.Week
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import com.example.walkrun.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: WalkRunApp,
    onOpenWorkout: (Long) -> Unit,
    onSettings: () -> Unit,
    onRecording: () -> Unit,
    onRecords: () -> Unit
) {
    val reports by app.settings.reports.collectAsStateWithLifecycle(initialValue = ReportSettings())
    val workouts by app.workouts.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val account by app.settings.account.collectAsStateWithLifecycle(initialValue = null)
    val configured by app.settings.profileConfigured.collectAsStateWithLifecycle(initialValue = true)
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val start = rememberWorkoutStarter(onRecording)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name?.let { "שלום, $it" } ?: "הליכה וריצה") },
                actions = {
                    IconButton(onClick = onRecords) {
                        Icon(Icons.Filled.Star, contentDescription = "שיאים אישיים")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "הגדרות")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!configured) {
                item {
                    Card(
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "הזן גובה, משקל, תאריך לידה ומין בהגדרות. בלי הנתונים האלה החישוב משתמש בערכי ברירת מחדל.",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                if (tracking.status != TrackingStatus.IDLE) {
                    Button(onClick = onRecording, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("חזרה לאימון הפעיל")
                    }
                } else {
                    // One start button. Walk or run is set later from the pace.
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RoundIconButton(
                            glyph = Glyph.PLAY,
                            color = StartGreen,
                            size = 128.dp,
                            description = "התחלת אימון"
                        ) { start(WorkoutType.WALK, null) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "הליכה או ריצה נקבעות אוטומטית לפי הקצב",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (reports.weeklyGoalKm > 0) {
                item { GoalCard(workouts, reports.weeklyGoalKm) }
            }
            item { Text("היסטוריה", style = MaterialTheme.typography.titleMedium) }
            if (workouts.isEmpty()) {
                item { Text("אין פעילויות שמורות.") }
            }
            items(workouts, key = { it.id }) { w ->
                WorkoutRow(
                    w = w,
                    // A new run of this route, with live comparison to this workout.
                    // Only when no workout is active, and the route is long enough to compare.
                    onRepeat = if (tracking.status == TrackingStatus.IDLE && w.distanceM >= MIN_REPEAT_M) {
                        { start(WorkoutType.from(w.type), w.id) }
                    } else null,
                    onClick = { onOpenWorkout(w.id) }
                )
            }
        }
    }
}

/** Progress of this week (Sunday to Saturday) toward the distance goal. */
@Composable
private fun GoalCard(workouts: List<WorkoutEntity>, goalKm: Double) {
    val (from, to) = remember(workouts) { Week.currentRange() }
    val doneKm = workouts.filter { it.startTime in from until to }.sumOf { it.distanceM } / 1000.0
    val progress = (doneKm / goalKm).toFloat().coerceIn(0f, 1f)
    val reached = doneKm >= goalKm
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 יעד שבועי", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.1f / %.1f ק\"מ", doneKm, goalKm),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = if (reached) StartGreen else MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
            Text(
                if (reached) "כל הכבוד! היעד הושג השבוע."
                else String.format(Locale.US, "נותרו %.1f ק\"מ · ", goalKm - doneKm) +
                    Week.daysLeft().let { if (it == 1) "היום האחרון בשבוע" else "$it ימים עד סוף השבוע" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Shorter workouts have too few points for a useful route comparison. */
private const val MIN_REPEAT_M = 100.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutRow(w: WorkoutEntity, onRepeat: (() -> Unit)?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                w.title?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${WorkoutType.from(w.type).label} · ${Format.date(w.startTime)}",
                        fontWeight = if (w.title == null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (w.records.isNotEmpty()) Text("🏆 ", style = MaterialTheme.typography.titleMedium)
                    TrendBadge(w.trend)
                }
                val route = if (w.routeId != null) " · מסלול חוזר" else ""
                Text(
                    "${Format.km(w.distanceM)} ק\"מ · ${Format.duration(w.durationMs)} · " +
                        "קצב ${Format.pace(Format.avgPace(w.durationMs, w.distanceM))}$route"
                )
            }
            if (onRepeat != null) {
                Spacer(Modifier.width(12.dp))
                RoundIconButton(
                    glyph = Glyph.PLAY,
                    color = StartGreen,
                    size = 48.dp,
                    description = "מסלול חדש בהשוואה לפעילות הזו",
                    onClick = onRepeat
                )
            }
        }
    }
}
