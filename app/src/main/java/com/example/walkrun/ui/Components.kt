package com.example.walkrun.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.walkrun.data.Trend
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.tracking.LatLon
import com.example.walkrun.tracking.TrackingService
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

// ---------------- Statistics grid ----------------

@Composable
fun StatGrid(stats: List<Pair<String, String>>, columns: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- Trend ----------------

@Composable
fun TrendBadge(trend: String?) {
    val t = Trend.from(trend) ?: return
    val (text, color) = when (t) {
        Trend.IMPROVED -> "▲ שיפור" to Color(0xFF2E7D32)
        Trend.REGRESSED -> "▼ נסיגה" to Color(0xFFC62828)
        Trend.SAME -> "= ללא שינוי" to Color(0xFF616161)
        Trend.FIRST -> "ראשונה" to Color(0xFF1565C0)
        Trend.NOT_COMPARABLE -> "לא ניתן להשוות" to Color(0xFF616161)
    }
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun GhostLine(diffMs: Long) {
    val sec = kotlin.math.abs(diffMs) / 1000
    val (text, color) = when {
        diffMs > 5000 -> "פיגור של $sec שניות אחרי הביצוע הקודם" to Color(0xFFC62828)
        diffMs < -5000 -> "הקדמה של $sec שניות על הביצוע הקודם" to Color(0xFF2E7D32)
        else -> "בקצב של הביצוע הקודם" to Color(0xFF616161)
    }
    Text(text, color = color, fontWeight = FontWeight.Bold)
}

// ---------------- Number field ----------------

/**
 * Number input with ▲▼ arrows. Each tap adds or subtracts [step], kept inside [min]..[max].
 * Holding an arrow repeats. Typing still works.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    decimal: Boolean = false,
    step: Double = if (decimal) 0.1 else 1.0,
    min: Double = 0.0,
    max: Double = 100_000.0,
    onChange: (String) -> Unit
) {
    // Number of decimals shown: the same as the step (0.5 -> 1, 0.1 -> 1, 1 -> 0).
    val decimals = if (!decimal) 0 else step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(1)
    fun shift(sign: Int) {
        val current = value.toDoubleOrNull() ?: min
        // Snap to the step grid, so 5.37 + 0.5 gives 5.5 and not 5.87.
        val next = (kotlin.math.round(current / step) + sign) * step
        val clamped = next.coerceIn(min, max)
        onChange(if (decimals == 0) clamped.roundToLong().toString() else String.format(Locale.US, "%.${decimals}f", clamped))
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        trailingIcon = { StepArrows(onUp = { shift(1) }, onDown = { shift(-1) }) },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Two small arrows, one above the other. Press and hold repeats. */
@Composable
fun StepArrows(onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RepeatArrow(Icons.Filled.KeyboardArrowUp, "הגדלה", onUp)
        RepeatArrow(Icons.Filled.KeyboardArrowDown, "הקטנה", onDown)
    }
}

@Composable
private fun RepeatArrow(icon: ImageVector, description: String, onStep: () -> Unit) {
    val current by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(width = 40.dp, height = 26.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        current()
                        delay(450)
                        while (true) { current(); delay(90) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------------- Map (OpenStreetMap) ----------------

@Composable
fun OsmMap(
    segments: List<List<LatLon>>,
    reference: List<LatLon>,
    followLast: Boolean,
    modifier: Modifier = Modifier,
    /** Heat map: route pieces with their own color. When set, it is drawn instead of [segments]. */
    colored: List<Pair<List<LatLon>, Int>>? = null
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val route = remember { RouteOverlays() }
    route.lineColor = routeColor
    val heatLines = remember { mutableListOf<Polyline>() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            // Remember when the user moves the map, so it does not jump back at once.
            setOnTouchListener { _, _ ->
                route.lastTouchRealtime = SystemClock.elapsedRealtime()
                false
            }
        }
    }
    var fitted by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // MapView draws tiles outside its own bounds. Clip it, so it does not cover the views around it.
    AndroidView(factory = { mapView }, modifier = modifier.clipToBounds(), update = { map ->
        // Dark theme: inverted (dark) map tiles.
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        // Heat map mode: replace the plain line with the colored pieces, one time per change.
        heatLines.forEach { map.overlays.remove(it) }
        heatLines.clear()
        colored?.forEach { (pts, color) ->
            if (pts.size > 1) heatLines += polyline(pts, color, 12f).also { map.overlays.add(it) }
        }
        // Only new points are added. The lines are not rebuilt on each update.
        val changed = route.sync(map, if (colored != null) emptyList() else segments, reference)

        if (followLast) {
            if (changed && !route.userMovedRecently()) {
                val last = segments.lastOrNull { it.isNotEmpty() }?.last() ?: reference.firstOrNull()
                last?.let { map.controller.setCenter(GeoPoint(it.lat, it.lon)) }
            }
            if (changed) map.invalidate()
            return@AndroidView
        }
        val all = segments.flatten()
        if (!fitted && all.size > 1) {
            fitted = true
            val box = BoundingBox.fromGeoPointsSafe(all.map { GeoPoint(it.lat, it.lon) })
            map.post { map.zoomToBoundingBox(box.increaseByScale(1.3f), false) }
        } else if (!fitted && all.size == 1) {
            fitted = true
            map.controller.setCenter(GeoPoint(all[0].lat, all[0].lon))
        }
        map.invalidate()
    })
}

private fun polyline(points: List<LatLon>, color: Int, width: Float) = Polyline().apply {
    setPoints(points.map { GeoPoint(it.lat, it.lon) })
    outlinePaint.color = color
    outlinePaint.strokeWidth = width
}

/** Keeps the map lines and adds only the new points on each update. */
private class RouteOverlays {
    var lastTouchRealtime = 0L
    var lineColor = DarkBlue.toArgb()
    private var refLine: Polyline? = null
    private var refSize = -1
    private val lines = mutableListOf<Polyline>()
    private val counts = mutableListOf<Int>()

    fun userMovedRecently() = SystemClock.elapsedRealtime() - lastTouchRealtime < FOLLOW_PAUSE_MS

    /** Returns true if a line changed. */
    fun sync(map: MapView, segments: List<List<LatLon>>, reference: List<LatLon>): Boolean {
        var changed = false
        if (reference.size != refSize) {
            refLine?.let { map.overlays.remove(it) }
            refLine = if (reference.size > 1) {
                polyline(reference, 0xFF9E9E9E.toInt(), 10f).also { map.overlays.add(0, it) }
            } else null
            refSize = reference.size
            changed = true
        }
        // A new workout starts again with fewer points: remove the old lines.
        val restarted = segments.size < lines.size ||
            counts.indices.any { segments[it].size < counts[it] }
        if (restarted) {
            lines.forEach { map.overlays.remove(it) }
            lines.clear()
            counts.clear()
            changed = true
        }
        segments.forEachIndexed { i, seg ->
            if (i == lines.size) {
                lines += polyline(emptyList(), lineColor, 12f).also { map.overlays.add(it) }
                counts += 0
            }
            if (seg.size > counts[i]) {
                for (k in counts[i] until seg.size) lines[i].addPoint(GeoPoint(seg[k].lat, seg[k].lon))
                counts[i] = seg.size
                changed = true
            }
        }
        return changed
    }

    private companion object {
        const val FOLLOW_PAUSE_MS = 15_000L
    }
}

// ---------------- Start a workout with runtime permissions ----------------

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * Returns a function that starts a workout.
 * It asks for missing permissions first. Precise location is mandatory. The other permissions are optional.
 */
@Composable
fun rememberWorkoutStarter(onStarted: () -> Unit): (WorkoutType, Long?, String?) -> Unit {
    val context = LocalContext.current
    val currentOnStarted by rememberUpdatedState(onStarted)
    var pending by remember { mutableStateOf<Triple<WorkoutType, Long?, String?>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pending
        pending = null
        if (request == null) return@rememberLauncherForActivityResult
        if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            TrackingService.start(context, request.first, request.second, request.third)
            currentOnStarted()
        } else {
            Toast.makeText(context, "נדרשת הרשאת מיקום מדויק כדי להקליט אימון.", Toast.LENGTH_LONG).show()
        }
    }

    return remember(context, launcher) {
        { type: WorkoutType, ghostId: Long?, planKey: String? ->
            val missing = requiredPermissions().filterNot { hasPermission(context, it) }
            if (missing.isEmpty()) {
                TrackingService.start(context, type, ghostId, planKey)
                currentOnStarted()
            } else {
                pending = Triple(type, ghostId, planKey)
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}
