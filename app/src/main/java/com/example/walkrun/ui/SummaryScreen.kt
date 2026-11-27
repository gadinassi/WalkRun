package com.example.walkrun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedButton
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
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.data.Analysis
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.example.walkrun.report.GpxExport
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.tracking.LatLon
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import com.example.walkrun.util.Format
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_TITLE = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(app: WalkRunApp, id: Long, onBack: () -> Unit, onRecording: () -> Unit) {
    var workout by remember { mutableStateOf<WorkoutEntity?>(null) }
    var segments by remember { mutableStateOf<List<List<LatLon>>>(emptyList()) }
    var points by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }
    var heatMap by remember { mutableStateOf(false) }
    /** Part of the route chosen for a new segment (meters from the start), shown in orange. */
    var segRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var compared by remember { mutableStateOf<WorkoutEntity?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val start = rememberWorkoutStarter(onRecording)

    LaunchedEffect(id) {
        val w = app.workouts.getWorkout(id)
        if (w == null) {
            notFound = true
            return@LaunchedEffect
        }
        points = app.workouts.getPoints(id)
        segments = points
            .groupBy { it.segment }
            .toSortedMap()
            .values
            .map { seg -> seg.map { LatLon(it.lat, it.lon) } }
        compared = w.comparedToId?.let { app.workouts.getWorkout(it) }
        workout = w
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("סיכום פעילות") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        val w = workout
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (notFound) Text("הפעילות לא נמצאה.") else CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val routeColor = MaterialTheme.colorScheme.primary.toArgb()
            if (segments.isNotEmpty()) {
                // Pace heat map: each ~100 m piece colored by its pace against the workout average.
                val heat = remember(points) {
                    val avg = Format.avgPace(w.durationMs, w.distanceM)
                    if (avg == null) null
                    else Analysis.pacePieces(points).map { (pts, pace) -> pts to Analysis.heatColor(pace, avg) }
                }
                OsmMap(
                    segments = segments,
                    reference = emptyList(),
                    followLast = false,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    colored = segRange?.let { r ->
                        // The whole route in the theme color, and the chosen part in orange on top.
                        segments.map { it to routeColor } + listOf(
                            points.filter { it.distanceM in r.start..r.endInclusive }.map { LatLon(it.lat, it.lon) } to 0xFFFF8F00.toInt()
                        )
                    } ?: if (heatMap) heat else null
                )
                if (heat != null && heat.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !heatMap, onClick = { heatMap = false }, label = { Text("מסלול") })
                        FilterChip(selected = heatMap, onClick = { heatMap = true }, label = { Text("מפת קצב") })
                        if (heatMap) HeatLegend()
                    }
                }
            }
            // Route title: tap to add or change it. All workouts of the same route get it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    w.title ?: "מסלול ללא שם",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (w.title == null) MaterialTheme.colorScheme.outline else Color.Unspecified,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { titleDraft = w.title ?: ""; editTitle = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "עריכת שם המסלול")
                }
            }
            Text(
                "${WorkoutType.from(w.type).label} · ${Format.date(w.startTime)}",
                style = MaterialTheme.typography.titleMedium
            )
            StatGrid(
                listOf(
                    "זמן" to Format.duration(w.durationMs),
                    "מרחק (ק\"מ)" to Format.km(w.distanceM),
                    "קצב ממוצע" to Format.pace(Format.avgPace(w.durationMs, w.distanceM)),
                    "מהירות מרבית (קמ\"ש)" to String.format(Locale.US, "%.1f", w.maxSpeedMps * 3.6),
                    (if (w.stepsEstimated) "צעדים (הערכה)" else "צעדים") to w.steps.toString(),
                    "קלוריות (הערכה)" to w.calories.roundToInt().toString(),
                    "טיפוס (מ')" to w.elevationGainM.roundToInt().toString()
                )
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("מגמה", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TrendBadge(w.trend)
                    }
                    w.trendText?.let { Text(it) }
                    compared?.let {
                        Text(
                            "הושווה ל: ${WorkoutType.from(it.type).label} מתאריך ${Format.date(it.startTime)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            WorkoutShoeRow(app, w.id, w.shoeId) {
                scope.launch { workout = app.workouts.getWorkout(w.id) }
            }

            if (w.records.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3C4), contentColor = Color(0xFF3A2E00))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🏆 שיא אישי חדש", fontWeight = FontWeight.Bold)
                        w.records.forEach { Text("${it.label}: ${it.display(w)}") }
                    }
                }
            }

            SplitsSection(app, w)

            val userProfile by app.settings.profile.collectAsStateWithLifecycle(initialValue = com.example.walkrun.data.UserProfile())
            HeartRateCard(points, w.avgHr, w.maxHr, com.example.walkrun.tracking.HrZones.maxHr(userProfile.age))

            val profile = remember(points) { Analysis.elevationProfile(points) }
            ElevationCard(profile, w.elevationGainM)

            WorkoutSegmentsCard(app, w)
            CreateSegmentCard(app, points) { segRange = it }

            Button(
                onClick = { start(WorkoutType.from(w.type), w.id, null) },
                enabled = tracking.status == TrackingStatus.IDLE && segments.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("חזור על המסלול והשווה") }

            if (points.size > 1) {
                OutlinedButton(
                    onClick = { GpxExport.share(context, w, points) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ייצוא GPX (Strava, Garmin)") }
            }

            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("מחיקת הפעילות")
            }
        }
    }

    if (editTitle) {
        AlertDialog(
            onDismissRequest = { editTitle = false },
            title = { Text("שם המסלול") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it.take(MAX_TITLE) },
                        singleLine = true,
                        placeholder = { Text("לדוגמה: סיבוב בפארק") }
                    )
                    Text(
                        "השם יופיע בכל הפעילויות של המסלול הזה.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editTitle = false
                    val current = workout ?: return@TextButton
                    scope.launch {
                        app.workouts.setRouteTitle(current, titleDraft)
                        workout = app.workouts.getWorkout(current.id)
                    }
                }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { editTitle = false }) { Text("ביטול") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את הפעילות?") },
            text = { Text("אי אפשר לשחזר פעילות שנמחקה.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.workouts.delete(id)
                        onBack()
                    }
                }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } }
        )
    }
}
