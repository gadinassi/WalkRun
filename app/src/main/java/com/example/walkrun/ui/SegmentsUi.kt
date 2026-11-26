package com.example.walkrun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.SegmentEffort
import com.example.walkrun.data.SegmentEntity
import com.example.walkrun.data.SegmentMatcher
import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.util.Format
import kotlinx.coroutines.launch

/**
 * On the summary screen: create a segment from part of this workout.
 * [onRange] reports the chosen part (meters from the start), so the map can show it.
 */
@Composable
fun CreateSegmentCard(
    app: WalkRunApp,
    points: List<TrackPointEntity>,
    onRange: (ClosedFloatingPointRange<Float>?) -> Unit
) {
    val total = points.lastOrNull()?.distanceM?.toFloat() ?: return
    if (total < 100f) return
    var open by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(0f..total) }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (!open) {
        TextButton(onClick = { open = true; onRange(range) }) { Text("➕ יצירת קטע מהמסלול (לטבלת שיאים)") }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("קטע חדש", fontWeight = FontWeight.Bold)
            Text("בחר את תחילת הקטע וסופו. החלק שנבחר מסומן בכתום על המפה.", style = MaterialTheme.typography.bodySmall)
            RangeSlider(
                value = range,
                onValueChange = { range = it; onRange(it) },
                valueRange = 0f..total
            )
            Text(
                "מ-${Format.km(range.start.toDouble())} עד ${Format.km(range.endInclusive.toDouble())} ק\"מ · " +
                    "אורך ${Format.km((range.endInclusive - range.start).toDouble())} ק\"מ",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("שם הקטע") },
                placeholder = { Text("לדוגמה: העלייה בפארק") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = name.isNotBlank() && range.endInclusive - range.start >= 50f,
                    onClick = {
                        val s = SegmentMatcher.create(name.trim(), points, range.start.toDouble(), range.endInclusive.toDouble())
                        if (s == null) {
                            android.widget.Toast.makeText(context, "הקטע קצר מדי (לפחות 50 מ').", android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        scope.launch {
                            app.workouts.addSegment(s)
                            android.widget.Toast.makeText(context, "הקטע \"${s.name}\" נוצר.", android.widget.Toast.LENGTH_SHORT).show()
                            open = false
                            name = ""
                            onRange(null)
                        }
                    }
                ) { Text("שמירת הקטע") }
                TextButton(onClick = { open = false; onRange(null) }) { Text("ביטול") }
            }
        }
    }
}

/** On the summary screen: this workout's times on the saved segments, with the rank. */
@Composable
fun WorkoutSegmentsCard(app: WalkRunApp, w: WorkoutEntity) {
    val segments by app.workouts.segments.collectAsStateWithLifecycle(initialValue = emptyList())
    var rows by remember { mutableStateOf<List<Triple<SegmentEntity, Long, Int>>>(emptyList()) }
    LaunchedEffect(segments, w.id) {
        rows = segments.mapNotNull { s ->
            val all = app.workouts.efforts(s)
            val i = all.indexOfFirst { it.workout.id == w.id }
            if (i < 0) null else Triple(s, all[i].timeMs, i + 1)
        }
    }
    if (rows.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("קטעים באימון הזה", fontWeight = FontWeight.Bold)
            rows.forEach { (s, ms, rank) ->
                Text("${if (rank == 1) "🏆 " else ""}${s.name}: ${Format.duration(ms)} · מקום $rank")
            }
        }
    }
}

/** All segments, each with its leaderboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentsScreen(app: WalkRunApp, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val segments by app.workouts.segments.collectAsStateWithLifecycle(initialValue = emptyList())
    var efforts by remember { mutableStateOf<Map<Long, List<SegmentEffort>>>(emptyMap()) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<SegmentEntity?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(segments) { efforts = segments.associate { it.id to app.workouts.efforts(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("קטעים") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (segments.isEmpty()) {
                Text("אין עדיין קטעים. פתח אימון, ובמסך הסיכום לחץ \"יצירת קטע מהמסלול\".")
            }
            segments.forEach { s ->
                val list = efforts[s.id].orEmpty()
                Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == s.id) null else s.id }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${Format.km(s.lengthM)} ק\"מ · ${list.size} מעברים" +
                                        (list.firstOrNull()?.let { " · שיא ${Format.duration(it.timeMs)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { confirmDelete = s }) {
                                Icon(Icons.Filled.Delete, contentDescription = "מחיקת הקטע")
                            }
                        }
                        if (expanded == s.id) {
                            list.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenWorkout(e.workout.id) }.padding(vertical = 4.dp)
                                ) {
                                    Text("${i + 1}. ${Format.duration(e.timeMs)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(
                                        "${WorkoutType.from(e.workout.type).label} · ${Format.date(e.workout.startTime)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("למחוק את הקטע \"${s.name}\"?") },
            text = { Text("האימונים עצמם לא נמחקים.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { app.workouts.deleteSegment(s.id) }
                }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("ביטול") } }
        )
    }
}
