package com.example.walkrun.ui

import android.annotation.SuppressLint
import android.widget.Toast
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.PlannedRouteEntity
import com.example.walkrun.data.RouteGeometry
import com.example.walkrun.data.RouteSnapper
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.tracking.LatLon
import com.example.walkrun.tracking.TrackingService
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import com.example.walkrun.util.Format
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** The saved planned routes: start one with navigation, or plan a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedRoutesScreen(app: WalkRunApp, onBack: () -> Unit, onNew: () -> Unit, onRecording: () -> Unit) {
    val routes by app.workouts.plannedRoutes.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val start = rememberWorkoutStarter(onRecording)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<PlannedRouteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("מסלולים מתוכננים") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("🗺️ תכנון מסלול חדש") }
            if (routes.isEmpty()) Text("אין עדיין מסלולים. תכנן מסלול על המפה, ובאימון תשמע הנחיות בכל פנייה.")
            routes.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${Format.km(r.lengthM)} ק\"מ · ${RouteGeometry.turns(r.points).size} פניות",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { confirmDelete = r }) { Icon(Icons.Filled.Delete, contentDescription = "מחיקה") }
                        RoundIconButton(
                            glyph = Glyph.PLAY, color = StartGreen, size = 48.dp,
                            description = "אימון עם ניווט במסלול", enabled = tracking.status == TrackingStatus.IDLE
                        ) { start(WorkoutType.WALK, null, TrackingService.ROUTE_PREFIX + r.id) }
                    }
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("למחוק את \"${r.name}\"?") },
            text = { Text("האימונים שנעשו במסלול לא נמחקים.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; scope.launch { app.workouts.deletePlannedRoute(r.id) } }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("ביטול") } }
        )
    }
}

/** Draw a route: each tap on the map adds a point; the line follows streets and paths. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun RoutePlannerScreen(app: WalkRunApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val waypoints = remember { mutableStateListOf<LatLon>() }
    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
    var snap by remember { mutableStateOf(true) }
    var loop by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf(false) }
    var snapFailed by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(32.08, 34.78))
        }
    }
    val line = remember { Polyline().apply { outlinePaint.strokeWidth = 12f } }
    val dots = remember { mutableListOf<Polygon>() }

    DisposableEffect(map) {
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                waypoints += LatLon(p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint) = false
        }))
        map.overlays.add(line)
        map.onResume()
        // Start where the user is (the location permission was given for the workouts).
        runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                if (loc != null && waypoints.isEmpty()) map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
            }
        }
        onDispose { map.onPause(); map.onDetach() }
    }

    // Recalculate the path after each change. A short wait joins quick taps into one request.
    LaunchedEffect(waypoints.toList(), snap, loop) {
        val pts = if (loop && waypoints.size >= 2) waypoints + waypoints.first() else waypoints.toList()
        if (pts.size < 2) { path = pts; return@LaunchedEffect }
        if (!snap) { path = pts; snapFailed = false; return@LaunchedEffect }
        delay(400)
        routing = true
        val snapped = RouteSnapper.route(pts)
        routing = false
        snapFailed = snapped == null
        path = snapped ?: pts
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("תכנון מסלול") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(), update = { m ->
                m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
                line.outlinePaint.color = lineColor
                line.setPoints(path.map { GeoPoint(it.lat, it.lon) })
                dots.forEach { m.overlays.remove(it) }
                dots.clear()
                waypoints.forEachIndexed { i, w ->
                    dots += Polygon().apply {
                        points = Polygon.pointsAsCircle(GeoPoint(w.lat, w.lon), 8.0)
                        fillPaint.color = if (i == 0) 0xFF2E7D32.toInt() else 0xFFFF8F00.toInt()
                        outlinePaint.color = 0xFFFFFFFF.toInt()
                        outlinePaint.strokeWidth = 3f
                    }.also { m.overlays.add(it) }
                }
                m.invalidate()
            })
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        waypoints.isEmpty() -> "הקש על המפה בנקודת ההתחלה, ואז בכל נקודה שבה רוצים לעבור."
                        routing -> "מחשב מסלול..."
                        else -> "${Format.km(RouteGeometry.length(path))} ק\"מ · ${RouteGeometry.turns(path).size} פניות"
                    },
                    fontWeight = FontWeight.Bold
                )
                if (snapFailed) Text("אין חיבור לשרת המסלולים. הקו ישר בין הנקודות.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = snap, onCheckedChange = { snap = it })
                    Text("צמוד לרחובות ולשבילים", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Checkbox(checked = loop, onCheckedChange = { loop = it })
                    Text("חזרה להתחלה", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = waypoints.isNotEmpty(), onClick = { waypoints.removeAt(waypoints.lastIndex) }, modifier = Modifier.weight(1f)) {
                        Text("ביטול נקודה")
                    }
                    OutlinedButton(enabled = waypoints.isNotEmpty(), onClick = { waypoints.clear() }, modifier = Modifier.weight(1f)) {
                        Text("ניקוי")
                    }
                    Button(enabled = path.size >= 2 && !routing, onClick = { askName = true }, modifier = Modifier.weight(1f)) {
                        Text("שמירה")
                    }
                }
                if (snap) {
                    Text(
                        "צמידה לשבילים שולחת רק את הנקודות שהקשת לשרת OpenStreetMap (routing.openstreetmap.de).",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    if (askName) {
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("שם המסלול") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    placeholder = { Text("לדוגמה: סיבוב הפארק הגדול") }
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    askName = false
                    val r = PlannedRouteEntity(
                        name = name.trim(), pointsText = RouteGeometry.encode(path), lengthM = RouteGeometry.length(path)
                    )
                    scope.launch {
                        app.workouts.addPlannedRoute(r)
                        Toast.makeText(context, "המסלול \"${r.name}\" נשמר.", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("ביטול") } }
        )
    }
}
