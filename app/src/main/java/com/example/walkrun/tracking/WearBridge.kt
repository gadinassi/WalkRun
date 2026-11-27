package com.example.walkrun.tracking

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.walkrun.util.Format
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Link to the WalkRun watch app (Wear OS Data Layer).
 * Phone -> watch: the live state, at most every 2 seconds and at every status change.
 * Watch -> phone: pause, resume and stop. A workout is started on the phone only:
 * Android does not give GPS to a workout started while the phone app is in the background.
 */
object WearBridge {
    const val STATE_PATH = "/walkrun/state"
    const val COMMAND_PATH = "/walkrun/cmd"
    const val HR_PATH = "/walkrun/hr"
    private const val MIN_GAP_MS = 2_000L
    private var lastPush = 0L
    private var lastStatus: TrackingStatus? = null

    fun push(context: Context, s: TrackingSnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (s.status == lastStatus && now - lastPush < MIN_GAP_MS) return
        lastPush = now
        lastStatus = s.status
        val req = PutDataMapRequest.create(STATE_PATH).apply {
            dataMap.putString("status", s.status.name)
            dataMap.putString("type", s.type.label)
            dataMap.putString("time", Format.duration(s.elapsedMs))
            dataMap.putString("km", Format.km(s.distanceM))
            dataMap.putString("pace", Format.pace(Format.paceFromSpeed(s.currentSpeedMps)))
            dataMap.putString("avg", Format.pace(Format.avgPace(s.elapsedMs, s.distanceM)))
            dataMap.putString("interval", s.intervalLabel ?: "")
            dataMap.putBoolean("auto", s.autoPaused)
            dataMap.putString("nav", s.navText ?: "")
            dataMap.putLong("at", System.currentTimeMillis()) // Makes every item new, so it is sent.
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(req)
            .addOnFailureListener { Log.d("WearBridge", "No watch: ${it.message}") }
    }
}

/** Receives the watch commands. */
class WearCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        // Heart rate from the watch sensor.
        if (event.path == WearBridge.HR_PATH) {
            String(event.data).toIntOrNull()?.let { HeartRateHub.update(it, "watch") }
            return
        }
        if (event.path != WearBridge.COMMAND_PATH) return
        val action = when (String(event.data)) {
            "pause" -> TrackingService.ACTION_PAUSE
            "resume" -> TrackingService.ACTION_RESUME
            "stop" -> TrackingService.ACTION_STOP
            else -> return
        }
        // Only acts on a running workout: the service is already in the foreground.
        if (TrackingStore.state.value.status == TrackingStatus.IDLE) return
        TrackingService.send(this, action)
    }
}
