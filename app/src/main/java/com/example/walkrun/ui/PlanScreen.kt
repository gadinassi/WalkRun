package com.example.walkrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.PlanSession
import com.example.walkrun.data.TrainingPlan
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(app: WalkRunApp, onBack: () -> Unit, onRecording: () -> Unit) {
    val active by app.settings.planActive.collectAsStateWithLifecycle(initialValue = false)
    val done by app.settings.planDone.collectAsStateWithLifecycle(initialValue = emptySet())
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val start = rememberWorkoutStarter(onRecording)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<PlanSession?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val next = TrainingPlan.next(done)
    val idle = tracking.status == TrackingStatus.IDLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("תוכנית אימונים") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(TrainingPlan.NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "3 אימונים בשבוע. כל אימון: 5 דקות הליכת חימום, סבבים של ריצה והליכה, ו-5 דקות הליכת שחרור. " +
                    "זמן הריצה גדל בהדרגה, עד 30 דקות ריצה ברצף בשבוע 8. ההודעות הקוליות אומרות מתי לרוץ ומתי ללכת.",
                style = MaterialTheme.typography.bodyMedium
            )
            val count = TrainingPlan.sessions.count { it.key in done }
            LinearProgressIndicator(
                progress = { count / TrainingPlan.sessions.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
            Text("הושלמו $count מתוך ${TrainingPlan.sessions.size} אימונים", style = MaterialTheme.typography.bodySmall)

            if (!active) {
                Button(
                    onClick = { scope.launch { app.settings.setPlanActive(true) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("התחלת התוכנית") }
            } else if (next != null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("האימון הבא: שבוע ${next.week}, אימון ${next.day}", fontWeight = FontWeight.Bold)
                            Text(next.description)
                            Text("כ-${next.totalMin} דקות עם חימום ושחרור", style = MaterialTheme.typography.bodySmall)
                        }
                        RoundIconButton(
                            glyph = Glyph.PLAY, color = StartGreen, size = 56.dp,
                            description = "התחלת האימון הבא בתוכנית", enabled = idle
                        ) { start(WorkoutType.RUN, null, next.key) }
                    }
                }
            } else {
                Text("🎉 סיימת את כל התוכנית! עכשיו אתה רץ 30 דקות ברצף.", fontWeight = FontWeight.Bold)
            }

            TrainingPlan.sessions.groupBy { it.week }.forEach { (week, sessions) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("שבוע $week", fontWeight = FontWeight.Bold)
                        Text(sessions.first().description, style = MaterialTheme.typography.bodySmall)
                    }
                    sessions.forEach { s ->
                        Spacer(Modifier.width(6.dp))
                        SessionDot(s, s.key in done, s == next) { chosen = s }
                    }
                }
            }

            if (active) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { app.settings.setPlanActive(false) } }, modifier = Modifier.weight(1f)) {
                        Text("הפסקת התוכנית")
                    }
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f)) { Text("התחלה מחדש") }
                }
            }
        }
    }

    chosen?.let { s ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("שבוע ${s.week}, אימון ${s.day}") },
            text = { Text("${s.description}\nכ-${s.totalMin} דקות עם חימום ושחרור.") },
            confirmButton = {
                TextButton(enabled = idle, onClick = {
                    chosen = null
                    scope.launch { app.settings.setPlanActive(true) }
                    start(WorkoutType.RUN, null, s.key)
                }) { Text("התחלת האימון") }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("סגירה") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("להתחיל את התוכנית מחדש?") },
            text = { Text("סימוני האימונים שהושלמו יימחקו. האימונים עצמם נשארים בהיסטוריה.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; scope.launch { app.settings.resetPlan() } }) { Text("התחלה מחדש") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("ביטול") } }
        )
    }
}

/** A session: a filled ✓ when done, an outlined circle for the next one. */
@Composable
private fun SessionDot(s: PlanSession, done: Boolean, isNext: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(if (done) StartGreen else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isNext) Modifier.border(3.dp, primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (done) "✓" else s.day.toString(),
            color = if (done) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
