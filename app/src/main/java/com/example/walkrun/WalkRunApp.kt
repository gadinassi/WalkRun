package com.example.walkrun

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.walkrun.data.AppDatabase
import com.example.walkrun.data.SettingsRepository
import com.example.walkrun.data.WorkoutRepository
import org.osmdroid.config.Configuration
import java.io.File

class WalkRunApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            // The OSM tile policy requires a User-Agent that identifies the app.
            // Generic values (like the default "com.example..." package name) can be blocked.
            // Before publishing, add contact details here, for example "WalkRun/1.0 (+https://your.site)".
            userAgentValue = "WalkRun/${BuildConfig.VERSION_NAME} (Android; personal use)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        val channel = NotificationChannel(CHANNEL_ID, "אימון פעיל", NotificationManager.IMPORTANCE_LOW)
        val reports = NotificationChannel(REPORT_CHANNEL_ID, "דוחות", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, reports))
    }

    companion object {
        const val CHANNEL_ID = "workout_tracking"
        const val REPORT_CHANNEL_ID = "reports"
    }
}
