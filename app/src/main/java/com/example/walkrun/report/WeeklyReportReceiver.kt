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
 * Weekly alarm (Sunday about 08:00) that prepares the weekly report.
 * Also sets the alarm again after the phone restarts.
 */
class WeeklyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(context)
            ACTION_WEEKLY -> {
                schedule(context) // The next Sunday.
                val app = context.applicationContext as WalkRunApp
                val pending = goAsync()
                scope.launch {
                    try {
                        if (app.settings.reports.first().weekly) Reports.notifyWeekly(context, app)
                    } catch (e: Exception) {
                        Log.w("Reports", "Weekly report failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_WEEKLY = "com.example.walkrun.WEEKLY_REPORT"
        private const val WINDOW_MS = 2 * 60 * 60 * 1000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * One alarm in a 2-hour window from Sunday 08:00. No special permission is needed.
         * A repeating inexact alarm could come days late, so each run sets the next one.
         */
        fun schedule(context: Context) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            var next = LocalDate.now(zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
            if (next <= now) next += AlarmManager.INTERVAL_DAY * 7
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WeeklyReportReceiver::class.java).setAction(ACTION_WEEKLY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            context.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pi)
        }
    }
}
