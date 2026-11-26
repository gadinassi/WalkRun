package com.example.walkrun.report

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.walkrun.WalkRunApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Scheduled jobs: the weekly report (Sunday about 08:00) and the nightly Drive backup (about 03:00).
 * Also sets the alarms again after the phone restarts or the app is updated.
 */
class WeeklyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(context)
            ACTION_WEEKLY -> {
                scheduleWeekly(context) // The next Sunday.
                runAsync(context) { app ->
                    if (app.settings.reports.first().weekly) Reports.notifyWeekly(context, app)
                }
            }
            ACTION_BACKUP -> {
                scheduleBackup(context) // The next night.
                runAsync(context) { app -> DriveBackup.nightly(app) }
            }
        }
    }

    private fun runAsync(context: Context, block: suspend (WalkRunApp) -> Unit) {
        val app = context.applicationContext as WalkRunApp
        val pending = goAsync()
        scope.launch {
            try {
                block(app)
            } catch (e: Exception) {
                Log.w("Jobs", "Scheduled job failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_WEEKLY = "com.example.walkrun.WEEKLY_REPORT"
        private const val ACTION_BACKUP = "com.example.walkrun.NIGHTLY_BACKUP"
        private const val WINDOW_MS = 2 * 60 * 60 * 1000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun schedule(context: Context) {
            scheduleWeekly(context)
            scheduleBackup(context)
        }

        /**
         * One alarm in a 2-hour window. No special permission is needed.
         * A repeating inexact alarm could come days late, so each run sets the next one.
         */
        private fun scheduleWeekly(context: Context) {
            val zone = ZoneId.systemDefault()
            var next = LocalDate.now(zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
            if (next <= System.currentTimeMillis()) next += AlarmManager.INTERVAL_DAY * 7
            set(context, ACTION_WEEKLY, 0, next)
        }

        private fun scheduleBackup(context: Context) {
            val zone = ZoneId.systemDefault()
            var next = LocalDate.now(zone).atTime(LocalTime.of(3, 0)).atZone(zone).toInstant().toEpochMilli()
            if (next <= System.currentTimeMillis()) next += AlarmManager.INTERVAL_DAY
            set(context, ACTION_BACKUP, 1, next)
        }

        private fun set(context: Context, action: String, code: Int, at: Long) {
            val pi = PendingIntent.getBroadcast(
                context, code,
                Intent(context, WeeklyReportReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
        }
    }
}
