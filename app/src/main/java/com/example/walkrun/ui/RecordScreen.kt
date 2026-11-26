package com.example.walkrun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.tracking.TrackingService
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import com.example.walkrun.util.Format
import kotlin.math.roundToInt

@Composable
fun RecordScreen(keepScreenOn: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by TrackingStore.state.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    // Screen stays on during the workout, so the numbers can be seen without unlocking.
    val view = LocalView.current
    val active = s.status == TrackingStatus.RECORDING || s.status == TrackingStatus.PAUSED
    DisposableEffect(keepScreenOn, active) {
        view.keepScreenOn = keepScreenOn && active
        onDispose { view.keepScreenOn = false }
    }

    val statusText = when (s.status) {
        TrackingStatus.RECORDING -> "מקליט"
        TrackingStatus.PAUSED -> if (s.autoPaused) "עצירה אוטומטית" else "מושהה"
        TrackingStatus.SAVING -> "שומר"
        TrackingStatus.IDLE -> "ממתין"
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("חזרה") }
            Text(
                "${s.type.label} · $statusText",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        OsmMap(
            segments = s.segments,
            reference = s.reference,
            followLast = true,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            s.intervalLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (it.startsWith("ריצה")) StopRed else StartGreen
                )
            }
            Text(
                Format.duration(s.elapsedMs),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            if (s.autoPaused) {
                Text(
                    "עצירה אוטומטית. האימון ימשיך לבד כשתזוז.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            StatGrid(
                listOf(
                    "מרחק (ק\"מ)" to Format.km(s.distanceM),
                    "קצב נוכחי" to Format.pace(Format.paceFromSpeed(s.currentSpeedMps)),
                    "קצב ממוצע" to Format.pace(Format.avgPace(s.elapsedMs, s.distanceM)),
                    (if (s.stepsEstimated) "צעדים (הערכה)" else "צעדים") to s.steps.toString(),
                    "קלוריות" to s.calories.roundToInt().toString(),
                    "טיפוס (מ')" to s.elevationGainM.roundToInt().toString()
                )
            )
            s.ghostDiffMs?.let { GhostLine(it) }

            when (s.status) {
                TrackingStatus.RECORDING, TrackingStatus.PAUSED -> {
                    val paused = s.status == TrackingStatus.PAUSED
                    // ‖ pause (▶ green when paused, to resume) and ■ red to finish.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundIconButton(
                            glyph = if (paused) Glyph.PLAY else Glyph.PAUSE,
                            color = if (paused) StartGreen else DarkBlue,
                            size = 88.dp,
                            description = if (paused) "המשך" else "השהיה"
                        ) {
                            TrackingService.send(
                                context,
                                if (paused) TrackingService.ACTION_RESUME else TrackingService.ACTION_PAUSE
                            )
                        }
                        RoundIconButton(
                            glyph = Glyph.STOP,
                            color = StopRed,
                            size = 88.dp,
                            description = "סיום"
                        ) { confirmStop = true }
                    }
                }
                TrackingStatus.SAVING -> Text("שומר את הפעילות...")
                TrackingStatus.IDLE -> Text("אין אימון פעיל.")
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("לסיים את האימון?") },
            text = { Text("האימון יישמר ותוצג השוואה לפעילות הקודמת.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    TrackingService.send(context, TrackingService.ACTION_STOP)
                }) { Text("סיום ושמירה") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("ביטול") } }
        )
    }
}
