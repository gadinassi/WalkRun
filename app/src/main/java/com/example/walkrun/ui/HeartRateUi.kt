package com.example.walkrun.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.tracking.HeartRateMonitor
import com.example.walkrun.tracking.HrZones
import com.example.walkrun.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Zone colors, from easy (blue) to max (red). */
val ZONE_COLORS = listOf(Color(0xFF64B5F6), Color(0xFF66BB6A), Color(0xFFFDD835), Color(0xFFFB8C00), Color(0xFFE53935))

/** Settings section: choose a Bluetooth heart rate strap. */
@SuppressLint("MissingPermission")
@Composable
fun HeartRateSettings(app: WalkRunApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val device by app.settings.hrDevice.collectAsStateWithLifecycle(initialValue = null)
    var scanning by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Pair<String, String>>() }

    val permissions = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                if (found.none { it.first == address }) {
                    val name = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName ?: "חיישן דופק"
                    found += address to name
                }
            }
        }
    }
    val scanner = remember { context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner }

    fun startScan() {
        if (scanner == null) {
            Toast.makeText(context, "Bluetooth כבוי או לא זמין.", Toast.LENGTH_LONG).show()
            return
        }
        found.clear()
        scanning = true
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateMonitor.SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback
            )
        }.onFailure {
            scanning = false
            Toast.makeText(context, "החיפוש נכשל: ${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            delay(12_000)
            if (scanning) { runCatching { scanner.stopScan(callback) }; scanning = false }
        }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) startScan()
        else Toast.makeText(context, "בלי הרשאת Bluetooth אי אפשר לחפש חיישן.", Toast.LENGTH_LONG).show()
    }

    DisposableEffect(Unit) { onDispose { if (scanning) runCatching { scanner?.stopScan(callback) } } }

    Text("חיישן דופק", style = MaterialTheme.typography.titleMedium)
    Text(
        device?.let { "מחובר: ${it.second}. הדופק נמדד בכל אימון." }
            ?: "רצועת חזה Bluetooth (Polar, Garmin, Wahoo ועוד). אפליקציית השעון של WalkRun שולחת דופק גם בלי חיישן.",
        style = MaterialTheme.typography.bodySmall
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = !scanning,
            onClick = {
                if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) startScan()
                else askPermission.launch(permissions)
            },
            modifier = Modifier.weight(1f)
        ) { Text(if (scanning) "מחפש..." else "חיפוש חיישן") }
        if (device != null) {
            OutlinedButton(
                onClick = { scope.launch { app.settings.setHrDevice(null, null) } },
                modifier = Modifier.weight(1f)
            ) { Text("הסרה") }
        }
    }

    if (scanning || found.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { runCatching { scanner?.stopScan(callback) }; scanning = false; found.clear() },
            title = { Text(if (scanning) "מחפש חיישני דופק..." else "חיישנים שנמצאו") },
            text = {
                Column {
                    if (found.isEmpty()) Text("הרטב את מגעי הרצועה ולבש אותה, כדי שתתחיל לשדר.")
                    found.forEach { (address, name) ->
                        TextButton(onClick = {
                            runCatching { scanner?.stopScan(callback) }
                            scanning = false
                            found.clear()
                            scope.launch { app.settings.setHrDevice(address, name) }
                            Toast.makeText(context, "$name נבחר.", Toast.LENGTH_SHORT).show()
                        }) { Text("$name  ($address)") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { runCatching { scanner?.stopScan(callback) }; scanning = false; found.clear() }) { Text("סגירה") }
            }
        )
    }
}

/** Summary card: heart rate over time, average and max, and the time in each zone. */
@Composable
fun HeartRateCard(points: List<TrackPointEntity>, avgHr: Int?, maxHr: Int?, userMaxHr: Int) {
    val samples = points.mapNotNull { p -> p.heartRate?.let { p.elapsedMs to it } }
    if (samples.size < 3 || avgHr == null) return
    val lo = samples.minOf { it.second } - 5
    val hi = samples.maxOf { it.second } + 5
    val t0 = samples.first().first
    val t1 = samples.last().first.coerceAtLeast(t0 + 1)
    val zones = HrZones.timeInZones(samples, userMaxHr)
    val zoneTotal = zones.sum().coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("❤ דופק", fontWeight = FontWeight.Bold)
            Text("ממוצע $avgHr · מרבי ${maxHr ?: "-"} · דופק מרבי משוער $userMaxHr", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width
                val h = size.height
                // Zone bands behind the line.
                for (z in 1..5) {
                    val from = (userMaxHr * (0.4 + z * 0.1)).toFloat()
                    val to = (userMaxHr * (0.5 + z * 0.1)).toFloat()
                    fun y(v: Float) = (h - (v - lo) / (hi - lo) * h).coerceIn(0f, h)
                    drawRect(ZONE_COLORS[z - 1].copy(alpha = 0.15f), Offset(0f, y(to)), androidx.compose.ui.geometry.Size(w, y(from) - y(to)))
                }
                val path = Path()
                samples.forEachIndexed { i, (t, bpm) ->
                    val x = ((t - t0).toFloat() / (t1 - t0)) * w
                    val y = h - (bpm - lo).toFloat() / (hi - lo) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(0xFFE53935), style = Stroke(width = 3f))
            }
            Text("זמן בכל אזור", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            for (z in 5 downTo 1) {
                val ms = zones[z - 1]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$z ${HrZones.NAMES[z - 1]}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
                    Box(Modifier.weight(1f).height(12.dp)) {
                        Box(
                            Modifier.fillMaxWidth((ms.toFloat() / zoneTotal).coerceAtLeast(0.01f)).height(12.dp)
                                .background(ZONE_COLORS[z - 1])
                        )
                    }
                    Text(" ${Format.duration(ms)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
