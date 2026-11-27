package com.example.walkrun.wear

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/** Live data of the workout on the phone, with pause / resume / stop. */
class WatchActivity : ComponentActivity(), DataClient.OnDataChangedListener, SensorEventListener {

    data class State(
        val status: String = "IDLE",
        val type: String = "",
        val time: String = "00:00",
        val km: String = "0.00",
        val pace: String = "--:--",
        val avg: String = "--:--",
        val interval: String = "",
        val auto: Boolean = false,
        val nav: String = "",
        val hr: Int? = null
    )

    private val state = mutableStateOf(State())
    private var lastHrSent = 0L
    private var hrRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1)
        }
        setContent { MaterialTheme { Screen(state.value, ::send) } }
    }

    /** Heart rate sensor on only while a workout runs on the phone, to save the battery. */
    private fun updateHrSensor() {
        val sm = getSystemService(SensorManager::class.java)
        val want = state.value.status != "IDLE" && state.value.status != "SAVING" &&
            checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (want && !hrRegistered && sensor != null) {
            hrRegistered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (!want && hrRegistered) {
            sm.unregisterListener(this)
            hrRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val bpm = event.values.firstOrNull()?.toInt() ?: return
        if (bpm <= 0) return
        state.value = state.value.copy(hr = bpm)
        val now = SystemClock.elapsedRealtime()
        if (now - lastHrSent < 2_000) return
        lastHrSent = now
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, HR_PATH, bpm.toString().toByteArray()) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onResume() {
        super.onResume()
        val client = Wearable.getDataClient(this)
        client.addListener(this)
        // The last state the phone sent, also when the app opens in the middle of a workout.
        client.dataItems.addOnSuccessListener { items ->
            items.forEach { if (it.uri.path == STATE_PATH) read(DataMapItem.fromDataItem(it)) }
            items.release()
        }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        if (hrRegistered) {
            getSystemService(SensorManager::class.java).unregisterListener(this)
            hrRegistered = false
        }
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == STATE_PATH) {
                read(DataMapItem.fromDataItem(e.dataItem))
            }
        }
    }

    private fun read(item: DataMapItem) {
        val m = item.dataMap
        state.value = State(
            status = m.getString("status", "IDLE"),
            type = m.getString("type", ""),
            time = m.getString("time", "00:00"),
            km = m.getString("km", "0.00"),
            pace = m.getString("pace", "--:--"),
            avg = m.getString("avg", "--:--"),
            interval = m.getString("interval", ""),
            auto = m.getBoolean("auto", false),
            nav = m.getString("nav", ""),
            hr = state.value.hr
        )
        updateHrSensor()
    }

    /** Sends a command to every connected phone (normally one). */
    private fun send(command: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, COMMAND_PATH, command.toByteArray()) }
        }
    }

    companion object {
        const val STATE_PATH = "/walkrun/state"
        const val COMMAND_PATH = "/walkrun/cmd"
        const val HR_PATH = "/walkrun/hr"
    }
}

@Composable
private fun Screen(s: WatchActivity.State, send: (String) -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        if (s.status == "IDLE" || s.status == "SAVING") {
            Text("WalkRun", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (s.status == "SAVING") "שומר את האימון..." else "התחל אימון באפליקציה בטלפון. הנתונים יופיעו כאן.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
            return@Column
        }
        if (s.interval.isNotEmpty()) {
            Text(s.interval, fontSize = 12.sp, color = if (s.interval.startsWith("ריצה")) Color(0xFFFF6E6E) else Color(0xFF7CD992))
        }
        Text(s.time, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${s.km} ק\"מ", fontSize = 20.sp)
        Text("קצב ${s.pace} · ממוצע ${s.avg}", fontSize = 12.sp)
        s.hr?.let { Text("❤ $it", fontSize = 14.sp, color = Color(0xFFFF6E6E)) }
        if (s.nav.isNotEmpty()) Text(s.nav, fontSize = 11.sp, textAlign = TextAlign.Center)
        if (s.status == "PAUSED") Text(if (s.auto) "עצירה אוטומטית" else "מושהה", fontSize = 12.sp, color = Color(0xFFF9A825))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val paused = s.status == "PAUSED"
            Button(
                onClick = { send(if (paused) "resume" else "pause") },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (paused) Color(0xFF2E7D32) else Color(0xFF0D47A1)),
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            ) { Text(if (paused) "▶" else "❚❚") }
            // Stop needs a second tap within 3 seconds, so a wrist bump does not end the workout.
            Button(
                onClick = {
                    if (confirmStop) { confirmStop = false; send("stop") } else confirmStop = true
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828)),
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            ) { Text(if (confirmStop) "✓" else "■") }
        }
        if (confirmStop) Text("לחץ שוב לסיום", fontSize = 11.sp)
    }
    LaunchedEffect(confirmStop) {
        if (confirmStop) { delay(3000); confirmStop = false }
    }
}
