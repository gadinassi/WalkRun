package com.example.walkrun.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.walkrun.MainActivity
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.AnnounceSettings
import android.util.Log
import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.data.TrainingSettings
import com.example.walkrun.data.Trend
import com.example.walkrun.data.Week
import com.example.walkrun.report.Reports
import com.example.walkrun.report.HealthSync
import com.example.walkrun.report.WalkRunWidget
import com.example.walkrun.data.UserProfile
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.util.Format
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground service that records one workout:
 * GPS location, distance, speed, steps, climb, calories, voice announcements and route comparison.
 */
class TrackingService : LifecycleService(), SensorEventListener {

    companion object {
        const val ACTION_START = "com.example.walkrun.START"
        const val ACTION_PAUSE = "com.example.walkrun.PAUSE"
        const val ACTION_RESUME = "com.example.walkrun.RESUME"
        const val ACTION_STOP = "com.example.walkrun.STOP"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_GHOST_ID = "ghost_id"
        private const val NOTIF_ID = 1

        private const val MAX_ACCURACY_M = 25f
        private const val MIN_STEP_M = 2.0
        private const val MAX_SPEED_MPS = 12.0
        private const val GHOST_TOLERANCE_MS = 5_000L
        private const val ALERT_INTERVAL_MS = 60_000L
        private const val OFF_ROUTE_M = 50f
        private const val MIN_ANNOUNCE_GAP_MS = 30_000L
        private const val PACE_WINDOW_MS = 30_000L
        /** 2.2 m/s = 7.9 km/h = pace 7:35 per km. Faster on average = run. */
        private const val RUN_SPEED_MPS = 2.2
        private const val AUTO_TYPE_MIN_M = 200.0
        private const val MIN_PACE_DISTANCE_M = 20.0
        /** No accepted movement for this long = the user stopped. */
        private const val AUTO_PAUSE_MS = 12_000L
        private const val AUTO_RESUME_M = 15f
        private const val PACE_ALERT_GAP_MS = 60_000L
        private const val PACE_ALERT_MIN_M = 200.0

        fun start(context: Context, type: WorkoutType, ghostWorkoutId: Long?) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_GHOST_ID, ghostWorkoutId ?: -1L)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, TrackingService::class.java).setAction(action))
        }
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var voice: VoiceAnnouncer
    private val app get() = application as WalkRunApp

    // Configuration of the current workout
    private var type = WorkoutType.WALK
    private var profile = UserProfile()
    private var announce = AnnounceSettings()
    private var ghost: GhostComparator? = null
    private var ghostId: Long? = null
    private var routeId: Long? = null
    /** A repeated route keeps the title of the route. */
    private var routeTitle: String? = null

    // Workout state
    private var status = TrackingStatus.IDLE
    private var startWallTime = 0L
    private var activeAccumMs = 0L
    private var segmentStartRealtime = 0L
    private var segment = 0
    private var distanceM = 0.0
    private var currentSpeed = 0f
    private var maxSpeed = 0.0
    private var activeKcal = 0.0
    private var lastLoc: Location? = null
    private var lastAlt: Double? = null
    private var lastAcceptRealtime = 0L
    private val points = mutableListOf<TrackPointEntity>()
    private val segments = mutableListOf<MutableList<LatLon>>()
    /** Read-only copy of [segments] for the UI. Updated only when the route changes, not on every tick. */
    private var segmentsSnapshot: List<List<LatLon>> = emptyList()

    // Steps
    private var useStepSensor = false
    private var stepsAccum = 0
    private var lastStepCounter: Float? = null

    // Altitude
    private var hasBarometer = false
    private var baroAlt: Double? = null
    private var elevation = ElevationGainTracker(4.0)

    // Announcements and comparison
    private var nextAnnounceDistance = 0.0
    private var nextAnnounceMs = 0L
    private var ghostState = 0 // -1 = faster, 1 = slower, 0 = unknown
    private var lastGhostAlert = 0L
    private var lastOffRouteAlert = 0L
    private var lastStatusAnnounce = 0L
    /** Weekly goal and the distance of this week's saved workouts, in meters. 0 = no goal. */
    private var goalM = 0.0
    private var weekDoneM = 0.0
    private var goalAnnounced = false

    // Training: auto-pause, pace alert, intervals
    private var training = TrainingSettings()
    /** True while the workout is paused because the user stopped moving (not the pause button). */
    private var autoPaused = false
    private var lastPaceAlert = 0L
    private var intervals: Intervals? = null
    private var intervalPhase = -1
    private var intervalWarned = -1

    private var tickJob: Job? = null
    private var stopJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        voice = VoiceAnnouncer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> if (status == TrackingStatus.IDLE) {
                stopJob?.cancel()
                stopJob = null
                reset()
                type = WorkoutType.from(intent.getStringExtra(EXTRA_TYPE))
                status = TrackingStatus.RECORDING
                startWallTime = System.currentTimeMillis()
                segmentStartRealtime = SystemClock.elapsedRealtime()
                if (!startForegroundSafe()) return START_NOT_STICKY
                val g = intent.getLongExtra(EXTRA_GHOST_ID, -1L).takeIf { it > 0 }
                lifecycleScope.launch { begin(g) }
            } else {
                // A workout is already active. The request is ignored, but the system requires
                // startForeground() after every startForegroundService(), else the app is stopped.
                runCatching {
                    ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), foregroundType())
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> lifecycleScope.launch { finish() }
        }
        if (status == TrackingStatus.IDLE && stopJob == null && intent?.action != ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSensorsAndLocation()
        voice.shutdown()
        super.onDestroy()
    }

    // ---------------- Lifecycle of a workout ----------------

    private fun reset() {
        ghost = null; ghostId = null; routeId = null; routeTitle = null
        activeAccumMs = 0; segment = 0; distanceM = 0.0; currentSpeed = 0f; maxSpeed = 0.0; activeKcal = 0.0
        lastLoc = null; lastAlt = null; lastAcceptRealtime = 0L
        points.clear(); segments.clear(); segments.add(mutableListOf()); refreshSegmentsSnapshot()
        useStepSensor = false; stepsAccum = 0; lastStepCounter = null
        hasBarometer = false; baroAlt = null; elevation = ElevationGainTracker(4.0)
        ghostState = 0; lastGhostAlert = 0L; lastOffRouteAlert = 0L; lastStatusAnnounce = 0L
        goalM = 0.0; weekDoneM = 0.0; goalAnnounced = false
        autoPaused = false; lastPaceAlert = 0L; intervals = null; intervalPhase = -1; intervalWarned = -1
    }

    private suspend fun begin(requestedGhostId: Long?) {
        profile = app.settings.profile.first()
        announce = app.settings.announce.first()
        training = app.settings.training.first()
        intervals = if (training.intervals) {
            Intervals(training.workSec * 1000L, training.restSec * 1000L, training.repeats.coerceAtLeast(1))
        } else null
        goalM = app.settings.reports.first().weeklyGoalKm * 1000.0
        if (goalM > 0) {
            val (from, to) = Week.currentRange()
            weekDoneM = app.workouts.between(from, to).sumOf { it.distanceM }
        }
        if (requestedGhostId != null) {
            val ref = app.workouts.getWorkout(requestedGhostId)
            val refPoints = app.workouts.getPoints(requestedGhostId)
            if (ref != null && refPoints.size > 1) {
                ghost = GhostComparator(refPoints)
                ghostId = ref.id
                routeId = ref.routeRootId
                routeTitle = ref.title
            }
        }
        // The user can stop the workout while the data loads.
        if (status != TrackingStatus.RECORDING && status != TrackingStatus.PAUSED) return
        nextAnnounceDistance = announce.distanceKm.coerceAtLeast(0.1) * 1000.0
        nextAnnounceMs = announce.timeMin.coerceAtLeast(1) * 60_000L

        if (!hasFineLocation()) {
            voice.speak("אין הרשאת מיקום. האימון לא נשמר.")
            abortWithoutSaving()
            return
        }
        registerSensors()
        requestLocation()
        startTicker()
        publish()
        voice.speak(
            if (ghost != null) "האימון התחיל. השוואה לביצוע קודם במסלול." else "האימון התחיל."
        )
        intervals?.let {
            voice.speak("אינטרוולים: ${it.repeats} סבבים של ${Format.durationSpeech(training.workSec * 1000L)} ריצה " +
                "ו-${Format.durationSpeech(training.restSec * 1000L)} הליכה.")
        }
    }

    /**
     * [auto]: the user stopped moving. The standing time since the last movement is not counted.
     * A manual pause during an auto-pause turns it into a manual pause.
     */
    private fun pause(auto: Boolean = false) {
        if (status == TrackingStatus.PAUSED && autoPaused && !auto) {
            autoPaused = false
            voice.speak("האימון הושהה.")
            publish()
            return
        }
        if (status != TrackingStatus.RECORDING) return
        val now = SystemClock.elapsedRealtime()
        val end = if (auto && lastAcceptRealtime > segmentStartRealtime) lastAcceptRealtime else now
        activeAccumMs += end - segmentStartRealtime
        status = TrackingStatus.PAUSED
        autoPaused = auto
        currentSpeed = 0f
        voice.speak(if (auto) "עצירה. השהיה אוטומטית." else "האימון הושהה.")
        publish()
        updateNotification()
    }

    /** Moved again after an auto-pause. The distance from the stop point is counted; the line continues. */
    private fun autoResume(loc: Location) {
        // The user started moving before the 15 m were reached. Count that time too:
        // distance from the stop point divided by the current speed (at most 15 seconds).
        val moved = lastLoc?.distanceTo(loc)?.toDouble() ?: 0.0
        val speed = if (loc.hasSpeed() && loc.speed > 0.5f) loc.speed.toDouble() else 1.3
        val walkedMs = (moved / speed * 1000).toLong().coerceIn(0L, 15_000L)
        segmentStartRealtime = SystemClock.elapsedRealtime() - walkedMs
        status = TrackingStatus.RECORDING
        autoPaused = false
        voice.speak("ממשיכים.")
        publish()
        updateNotification()
    }

    private fun resume() {
        if (status != TrackingStatus.PAUSED) return
        autoPaused = false
        segmentStartRealtime = SystemClock.elapsedRealtime()
        segment++
        segments.add(mutableListOf())
        refreshSegmentsSnapshot()
        // Do not count the distance or the climb between the pause point and the resume point.
        lastLoc = null
        lastAlt = null
        elevation.reset()
        status = TrackingStatus.RECORDING
        voice.speak("האימון חודש.")
        publish()
        updateNotification()
    }

    private suspend fun finish() {
        if (status == TrackingStatus.IDLE || status == TrackingStatus.SAVING) {
            if (status == TrackingStatus.IDLE && stopJob == null) stopSelf()
            return
        }
        if (status == TrackingStatus.RECORDING) {
            activeAccumMs += SystemClock.elapsedRealtime() - segmentStartRealtime
        }
        status = TrackingStatus.SAVING
        stopSensorsAndLocation()
        publish()

        val duration = activeAccumMs
        val reports = app.settings.reports.first()
        if (distanceM < reports.minSaveKm * 1000.0) {
            // Too short (for example, stopped right after start): not saved in the history.
            status = TrackingStatus.IDLE
            TrackingStore.update(TrackingSnapshot(discardedMinKm = reports.minSaveKm))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            voice.speak("האימון קצר מ-${Format.distanceSpeech(reports.minSaveKm * 1000.0)} ולא נשמר.")
            stopJob = lifecycleScope.launch {
                delay(8_000)
                stopSelf()
            }
            return
        }
        type = autoType()
        val (steps, estimated) = stepsInfo()
        val workout = WorkoutEntity(
            type = type.name,
            startTime = startWallTime,
            endTime = System.currentTimeMillis(),
            durationMs = duration,
            distanceM = distanceM,
            steps = steps,
            stepsEstimated = estimated,
            calories = totalCalories(duration),
            elevationGainM = elevation.gain,
            avgSpeedMps = if (duration > 0) distanceM / (duration / 1000.0) else 0.0,
            maxSpeedMps = maxSpeed,
            routeId = routeId,
            ghostWorkoutId = ghostId,
            title = routeTitle
        )
        val id = app.workouts.save(workout, points.toList())
        val saved = app.workouts.getWorkout(id)

        status = TrackingStatus.IDLE
        TrackingStore.update(TrackingSnapshot(lastSavedWorkoutId = id))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val records = saved?.records.orEmpty()
        val recordText = if (records.isEmpty()) "" else
            " שיא אישי חדש! " + records.joinToString(". ") { "${it.speech}: ${it.spoken(saved!!)}" } + "."
        val segmentText = saved?.let { w ->
            runCatching { app.workouts.segmentRecords(w) }.getOrDefault(emptyList())
                .joinToString("") { (s, ms) -> " שיא חדש בקטע ${s.name}: ${Format.durationSpeech(ms)}." }
        }.orEmpty()
        val goalText = if (goalM > 0) {
            val left = goalM - weekDoneM - distanceM
            if (left > 0) " ליעד השבועי נותרו ${Format.distanceSpeech(left)}." else ""
        } else ""
        voice.speak("האימון הסתיים. ${saved?.trendText ?: ""}$recordText$segmentText$goalText")
        saved?.let { HealthSync.afterSave(app, it) }
        WalkRunWidget.refresh(this)
        if (saved != null && reports.onImprovement && (saved.trend == Trend.IMPROVED.name || records.isNotEmpty())) {
            runCatching { Reports.notifyImprovement(this, app, saved) }
                .onFailure { Log.w("Reports", "Improvement report failed", it) }
        }
        // Keep the service alive for a short time, so the final announcement can complete.
        stopJob = lifecycleScope.launch {
            delay(12_000)
            stopSelf()
        }
    }

    private fun abortWithoutSaving() {
        status = TrackingStatus.IDLE
        stopSensorsAndLocation()
        TrackingStore.update(TrackingSnapshot())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------- Location ----------------

    private fun hasFineLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onLocation(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        // Auto-paused: resume when the user moved away from the stop point.
        if (status == TrackingStatus.PAUSED && autoPaused) {
            val stop = lastLoc
            if (stop == null || stop.distanceTo(loc) >= AUTO_RESUME_M) autoResume(loc) else return
        }
        if (status != TrackingStatus.RECORDING) return

        val alt = currentAltitude(loc)
        val elapsed = elapsedMs()
        val prev = lastLoc
        if (prev == null) {
            accept(loc, alt, elapsed, 0.0)
            return
        }
        val d = prev.distanceTo(loc).toDouble()
        val dt = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
        if (dt <= 0) return
        if (d < MIN_STEP_M) return            // GPS noise while standing
        val v = d / dt
        if (v > MAX_SPEED_MPS) return         // GPS jump

        val prevAlt = lastAlt
        val grade = if (alt != null && prevAlt != null) ((alt - prevAlt) / d).coerceIn(0.0, 0.2) else 0.0
        activeKcal += Metrics.activeKcal(type, v, grade, dt, profile.weightKg)
        distanceM += d
        if (v > maxSpeed) maxSpeed = v

        accept(loc, alt, elapsed, v)
        checkGhost(loc)
        checkDistanceAnnouncement()
        checkWeeklyGoal()
    }

    private fun accept(loc: Location, alt: Double?, elapsed: Long, speed: Double) {
        lastLoc = loc
        lastAlt = alt
        lastAcceptRealtime = SystemClock.elapsedRealtime()
        if (!hasBarometer && alt != null) elevation.add(alt)
        currentSpeed = if (loc.hasSpeed()) loc.speed else speed.toFloat()
        segments.last().add(LatLon(loc.latitude, loc.longitude))
        refreshSegmentsSnapshot()
        points.add(
            TrackPointEntity(
                workoutId = 0,
                time = loc.time,
                elapsedMs = elapsed,
                lat = loc.latitude,
                lon = loc.longitude,
                altitude = alt,
                speedMps = speed.toFloat(),
                distanceM = distanceM,
                steps = stepsInfo().first,
                segment = segment
            )
        )
        publish()
    }

    private fun currentAltitude(loc: Location): Double? = when {
        hasBarometer -> baroAlt
        loc.hasAltitude() -> loc.altitude
        else -> null
    }

    // ---------------- Sensors ----------------

    private fun registerSensors() {
        val stepPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null && stepPermission) {
            useStepSensor = sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
        val pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressure != null && sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL)) {
            hasBarometer = true
            elevation = ElevationGainTracker(1.0)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                // The counter is cumulative since boot. Count only the increase while recording.
                val value = event.values[0]
                val last = lastStepCounter
                if (last != null && status == TrackingStatus.RECORDING) {
                    stepsAccum += (value - last).toInt().coerceAtLeast(0)
                }
                lastStepCounter = value
            }
            Sensor.TYPE_PRESSURE -> {
                val alt = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]
                ).toDouble()
                val prev = baroAlt
                val smoothed = if (prev == null) alt else prev + 0.1 * (alt - prev)
                baroAlt = smoothed
                if (status == TrackingStatus.RECORDING) elevation.add(smoothed)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun stopSensorsAndLocation() {
        fused.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        tickJob?.cancel()
        tickJob = null
    }

    // ---------------- Calculations ----------------

    private fun elapsedMs(): Long = activeAccumMs +
        if (status == TrackingStatus.RECORDING) SystemClock.elapsedRealtime() - segmentStartRealtime else 0L

    private fun totalCalories(elapsed: Long) = activeKcal + Metrics.restingKcal(profile, elapsed)

    /** Returns steps and a flag: true if the value is an estimate from distance and stride length. */
    private fun stepsInfo(): Pair<Int, Boolean> {
        if (useStepSensor && (stepsAccum > 0 || distanceM < 200)) return stepsAccum to false
        return (distanceM / Metrics.strideM(profile, type)).toInt() to true
    }

    private fun ghostDiffMs(elapsed: Long): Long? {
        val g = ghost ?: return null
        if (distanceM < 100) return null
        val ref = g.timeAtDistance(distanceM) ?: return null
        return elapsed - ref
    }

    // ---------------- Announcements ----------------

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = lifecycleScope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                if (status == TrackingStatus.RECORDING) {
                    if (SystemClock.elapsedRealtime() - lastAcceptRealtime > 6000) currentSpeed = 0f
                    checkTimeAnnouncement()
                    checkIntervals()
                    checkPaceAlert()
                    checkAutoPause()
                }
                publish()
                if (++n % 5 == 0) updateNotification()
            }
        }
    }

    private fun checkDistanceAnnouncement() {
        if (!announce.enabled || !announce.byDistance) return
        if (distanceM < nextAnnounceDistance) return
        val step = announce.distanceKm.coerceAtLeast(0.1) * 1000.0
        while (nextAnnounceDistance <= distanceM) nextAnnounceDistance += step
        speakStatus()
    }

    private fun checkAutoPause() {
        if (!training.autoPause || points.isEmpty()) return
        // Start after the first movement, and only from a recording segment.
        val since = SystemClock.elapsedRealtime() - maxOf(lastAcceptRealtime, segmentStartRealtime)
        if (since >= AUTO_PAUSE_MS) pause(auto = true)
    }

    /** Spoken when the recent pace is outside the range in the settings. */
    private fun checkPaceAlert() {
        if (!training.paceAlert || distanceM < PACE_ALERT_MIN_M) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPaceAlert < PACE_ALERT_GAP_MS) return
        val pace = recentPaceSecPerKm()?.takeIf { it <= 3600 } ?: return
        val text = when {
            pace > training.slowPaceSec -> "קצב איטי, ${Format.paceSpeech(pace)} לקילומטר. הגבר."
            pace < training.fastPaceSec -> "קצב מהיר, ${Format.paceSpeech(pace)} לקילומטר. האט."
            else -> return
        }
        lastPaceAlert = now
        voice.speak(text)
    }

    /** Announces each change of phase, and 5 seconds before it. */
    private fun checkIntervals() {
        val iv = intervals ?: return
        val p = iv.at(elapsedMs())
        if (p.index != intervalPhase) {
            intervalPhase = p.index
            voice.speak(
                when {
                    p.remainingMs == null -> "סיימת את כל ${iv.repeats} הסבבים. כל הכבוד!"
                    p.work -> "ריצה! ${Format.durationSpeech(training.workSec * 1000L)}. סבב ${p.round} מתוך ${iv.repeats}."
                    else -> "הליכה. ${Format.durationSpeech(training.restSec * 1000L)} מנוחה."
                }
            )
            return
        }
        val left = p.remainingMs ?: return
        if (left <= 5_000 && intervalWarned != p.index) {
            intervalWarned = p.index
            voice.speak("עוד 5 שניות.")
        }
    }

    /** Text for the workout screen, for example "ריצה · סבב 3/8 · 1:12". */
    private fun intervalLabel(): String? {
        val iv = intervals ?: return null
        val p = iv.at(elapsedMs())
        val left = p.remainingMs ?: return "אינטרוולים: הסתיים"
        return "${if (p.work) "ריצה" else "הליכה"} · סבב ${p.round}/${iv.repeats} · ${Format.duration(left + 999)}"
    }

    /** One announcement when this workout brings the week to the goal. */
    private fun checkWeeklyGoal() {
        if (goalM <= 0 || goalAnnounced || weekDoneM >= goalM) return
        if (weekDoneM + distanceM < goalM) return
        goalAnnounced = true
        voice.speak("כל הכבוד! הגעת ליעד השבועי של ${Format.kmSpeech(goalM)} קילומטר.")
    }

    private fun checkTimeAnnouncement() {
        if (!announce.enabled || !announce.byTime) return
        val e = elapsedMs()
        if (e < nextAnnounceMs) return
        val step = announce.timeMin.coerceAtLeast(1) * 60_000L
        while (nextAnnounceMs <= e) nextAnnounceMs += step
        speakStatus()
    }

    /**
     * With both modes on, a distance point and a time point can come close together.
     * The same status is not spoken twice within [MIN_ANNOUNCE_GAP_MS].
     */
    private fun speakStatus() {
        val now = SystemClock.elapsedRealtime()
        if (lastStatusAnnounce != 0L && now - lastStatusAnnounce < MIN_ANNOUNCE_GAP_MS) return
        lastStatusAnnounce = now
        voice.speak(buildAnnouncement())
    }

    /**
     * Order: distance, time, current pace, average pace,
     * then (only with route comparison) remaining distance and the difference from the reference.
     */
    private fun buildAnnouncement(): String {
        val e = elapsedMs()
        val sb = StringBuilder()
        sb.append("מרחק ${Format.kmSpeech(distanceM)} קילומטר. ")
        sb.append("זמן ${Format.durationSpeech(e)}. ")
        // Slower than 60 minutes per km is treated as standing.
        val current = recentPaceSecPerKm()?.takeIf { it <= 3600 }
        if (current != null) {
            sb.append("קצב נוכחי ${Format.paceSpeech(current)} לקילומטר. ")
        } else {
            sb.append("קצב נוכחי: עצירה. ")
        }
        if (distanceM >= 100) {
            sb.append("קצב ממוצע ${Format.paceSpeech(e / 1000.0 / (distanceM / 1000.0))} לקילומטר. ")
        }
        ghost?.let { g ->
            val remaining = g.totalDistance - distanceM
            sb.append(
                if (remaining > 0) "נותרו ${Format.distanceSpeech(remaining)} לפי המסלול. "
                else "עברת את אורך המסלול של הביצוע הקודם. "
            )
        }
        ghostDiffMs(e)?.let { sb.append(ghostPhrase(it)) }
        val toGoal = goalM - weekDoneM - distanceM
        if (goalM > 0 && toGoal > 0) sb.append("ליעד השבועי נותרו ${Format.distanceSpeech(toGoal)}. ")
        return sb.toString()
    }

    /**
     * Pace over the last [PACE_WINDOW_MS] of the current segment, in seconds per km.
     * The instant GPS speed jumps too much to be spoken. Null = standing, or not enough data.
     */
    private fun recentPaceSecPerKm(): Double? {
        val last = points.lastOrNull() ?: return null
        // No accepted point for some time: the user stopped.
        if (SystemClock.elapsedRealtime() - lastAcceptRealtime > 10_000) return null
        var first = last
        for (i in points.indices.reversed()) {
            val p = points[i]
            if (p.segment != last.segment || last.elapsedMs - p.elapsedMs > PACE_WINDOW_MS) break
            first = p
        }
        val dd = last.distanceM - first.distanceM
        val dt = (last.elapsedMs - first.elapsedMs) / 1000.0
        if (dd >= MIN_PACE_DISTANCE_M && dt > 0) return dt / (dd / 1000.0)
        // Fallback: the GPS speed, if the window is too short (start, or just after a pause).
        return if (currentSpeed >= 0.3f) 1000.0 / currentSpeed else null
    }

    private fun ghostPhrase(diffMs: Long): String {
        val sec = abs(diffMs) / 1000
        return when {
            diffMs > GHOST_TOLERANCE_MS -> "פיגור של $sec שניות אחרי הביצוע הקודם."
            diffMs < -GHOST_TOLERANCE_MS -> "הקדמה של $sec שניות על הביצוע הקודם."
            else -> "בקצב של הביצוע הקודם."
        }
    }

    /** Announces when the state changes between faster and slower than the reference, and when off route. */
    private fun checkGhost(loc: Location) {
        val g = ghost ?: return
        val now = SystemClock.elapsedRealtime()

        val off = g.distanceFromRoute(loc.latitude, loc.longitude)
        if (off > OFF_ROUTE_M && now - lastOffRouteAlert > ALERT_INTERVAL_MS) {
            lastOffRouteAlert = now
            voice.speak("סטייה מהמסלול: ${off.toInt()} מטר.")
        }

        val diff = ghostDiffMs(elapsedMs()) ?: return
        val newState = when {
            diff > GHOST_TOLERANCE_MS -> 1
            diff < -GHOST_TOLERANCE_MS -> -1
            else -> ghostState
        }
        if (newState != ghostState && now - lastGhostAlert > ALERT_INTERVAL_MS) {
            ghostState = newState
            lastGhostAlert = now
            val head = if (newState == 1) "קצב איטי מהביצוע הקודם." else "קצב מהיר מהביצוע הקודם."
            voice.speak("$head ${ghostPhrase(diff)}")
        }
    }

    // ---------------- State and notification ----------------

    /**
     * Walk or run is not chosen by the user. It follows the average speed of the workout so far.
     * The first [AUTO_TYPE_MIN_M] keep the start value, because the average is not stable yet.
     */
    private fun autoType(): WorkoutType {
        val e = elapsedMs()
        if (distanceM < AUTO_TYPE_MIN_M || e <= 0) return type
        return if (distanceM / (e / 1000.0) >= RUN_SPEED_MPS) WorkoutType.RUN else WorkoutType.WALK
    }

    private fun publish() {
        type = autoType()
        val elapsed = elapsedMs()
        val (steps, estimated) = stepsInfo()
        TrackingStore.update(
            TrackingSnapshot(
                status = status,
                type = type,
                elapsedMs = elapsed,
                distanceM = distanceM,
                currentSpeedMps = currentSpeed,
                steps = steps,
                stepsEstimated = estimated,
                calories = totalCalories(elapsed),
                elevationGainM = elevation.gain,
                segments = segmentsSnapshot,
                reference = ghost?.path ?: emptyList(),
                ghostDiffMs = ghostDiffMs(elapsed),
                autoPaused = autoPaused,
                intervalLabel = intervalLabel()
            )
        )
    }

    /**
     * Earlier segments do not change after a pause, so their copies are reused.
     * Only the current segment is copied again.
     */
    private fun refreshSegmentsSnapshot() {
        val kept = segmentsSnapshot.take(segments.size - 1)
        segmentsSnapshot = if (kept.size == segments.size - 1) kept + listOf(segments.last().toList())
        else segments.map { it.toList() }
    }

    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else 0

    private fun startForegroundSafe(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), foregroundType())
        true
    } catch (e: Exception) {
        // Example: the location permission was removed before the start.
        status = TrackingStatus.IDLE
        stopSelf()
        false
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val paused = status == TrackingStatus.PAUSED
        val toggleLabel = if (paused) "המשך" else "השהיה"
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        return NotificationCompat.Builder(this, WalkRunApp.CHANNEL_ID)
            .setSmallIcon(com.example.walkrun.R.drawable.ic_launcher_monochrome)
            .setContentTitle("${type.label} · ${if (paused) (if (autoPaused) "עצירה אוטומטית" else "מושהה") else "פעיל"}")
            .setContentText("${Format.km(distanceM)} ק\"מ · ${Format.duration(elapsedMs())}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, toggleLabel, servicePending(toggleAction, 1))
            .addAction(0, "סיום", servicePending(ACTION_STOP, 2))
            .build()
    }

    private fun servicePending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, TrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun updateNotification() {
        if (status != TrackingStatus.RECORDING && status != TrackingStatus.PAUSED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
