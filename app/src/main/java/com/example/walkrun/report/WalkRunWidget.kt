package com.example.walkrun.report

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.walkrun.MainActivity
import com.example.walkrun.R
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.Week
import com.example.walkrun.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home screen widget. The ▶ opens the app and starts a workout there: a location workout must
 * start from a visible screen, the widget alone is not allowed to start one.
 */
class WalkRunWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_START = "start_workout"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Call after a workout is saved or the goal changed. Does nothing when no widget is placed. */
        fun refresh(context: Context) {
            scope.launch { runCatching { render(context) } }
        }

        private suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WalkRunWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as WalkRunApp
            val (from, to) = Week.currentRange()
            val week = app.workouts.between(from, to)
            val km = week.sumOf { it.distanceM } / 1000.0
            val goal = app.settings.reports.first().weeklyGoalKm
            val last = app.workouts.allOnce().lastOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_walkrun)
            views.setTextViewText(
                R.id.widget_week,
                if (goal > 0) String.format(Locale.US, "השבוע %.1f / %.0f ק\"מ", km, goal)
                else String.format(Locale.US, "השבוע %.1f ק\"מ · %d אימונים", km, week.size)
            )
            views.setProgressBar(R.id.widget_progress, 100, if (goal > 0) ((km / goal) * 100).toInt().coerceIn(0, 100) else 0, false)
            views.setViewVisibility(R.id.widget_progress, if (goal > 0) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(
                R.id.widget_last,
                last?.let { "אחרון: ${Format.km(it.distanceM)} ק\"מ · קצב ${Format.pace(Format.avgPace(it.durationMs, it.distanceM))}" }
                    ?: "עדיין אין אימונים"
            )
            val open = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val start = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_START, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_play, start)
            manager.updateAppWidget(ids, views)
        }
    }
}
