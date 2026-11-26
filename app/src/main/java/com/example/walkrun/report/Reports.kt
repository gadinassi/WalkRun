package com.example.walkrun.report

import android.Manifest
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.walkrun.MainActivity
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.Account
import com.example.walkrun.data.SplitUnit
import com.example.walkrun.data.Week
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Email reports with chart images: a weekly summary, and a report after an improved workout.
 * The app prepares the email; the user sends it from the email app with one tap.
 */
object Reports {
    private const val DAY_MS = 86_400_000L
    private const val WEEKS_IN_TREND = 8
    private const val GMAIL = "com.google.android.gm"
    private const val NOTIFY_WEEKLY = 100
    private const val NOTIFY_IMPROVED = 101
    private val DAY_FMT = DateTimeFormatter.ofPattern("dd/MM")
    private val DAY_NAMES = listOf("ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳", "א׳") // Monday..Sunday

    data class Email(val subject: String, val body: String, val images: List<File>)

    // ---------------- Weekly ----------------

    /** Null when there were no workouts in the last two weeks. */
    suspend fun weekly(context: Context, app: WalkRunApp): Email? = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        // Week = Sunday to Saturday, like the goal. On Sunday the report is for the week that just ended.
        val start = if (now.dayOfWeek == java.time.DayOfWeek.SUNDAY) now.minusDays(7) else Week.startOf(now)
        val today = minOf(start.plusDays(6), now) // Last day with data, for the title.
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ms(start.plusDays(7))
        val goalKm = app.settings.reports.first().weeklyGoalKm
        val thisWeek = app.workouts.between(ms(start), end)
        val lastWeek = app.workouts.between(ms(start.minusDays(7)), ms(start))
        if (thisWeek.isEmpty() && lastWeek.isEmpty()) return@withContext null

        val dir = reportDir(context)
        val days = (0..6).map { start.plusDays(it.toLong()) }
        fun kmPerDay(list: List<WorkoutEntity>, offsetDays: Long) = days.map { d ->
            val day = d.minusDays(offsetDays)
            list.filter { localDate(it.startTime, zone) == day }.sumOf { it.distanceM } / 1000.0
        }
        val images = mutableListOf<File>()
        images += ChartImages.bars(
            File(dir, "weekly_km.png"),
            "מרחק יומי (ק\"מ)",
            days.map { DAY_NAMES[it.dayOfWeek.value - 1] },
            kmPerDay(thisWeek, 0), kmPerDay(lastWeek, 7),
            "השבוע", "שבוע קודם"
        ) { String.format(Locale.US, "%.1f", it) }

        // Weekly average pace for the main type of the last 8 weeks.
        val trendFrom = start.minusDays(7L * (WEEKS_IN_TREND - 1))
        val trendList = app.workouts.between(ms(trendFrom), end)
        val mainType = trendList.groupBy { it.type }.maxByOrNull { (_, l) -> l.sumOf { it.distanceM } }?.key
        if (mainType != null) {
            val weeks = (0 until WEEKS_IN_TREND).map { trendFrom.plusDays(7L * it) }
            val paces = weeks.map { w ->
                val l = trendList.filter {
                    it.type == mainType && localDate(it.startTime, zone).let { d -> !d.isBefore(w) && d.isBefore(w.plusDays(7)) }
                }
                Format.avgPace(l.sumOf { it.durationMs }, l.sumOf { it.distanceM })
            }
            if (paces.count { it != null } >= 1) {
                images += ChartImages.paceLines(
                    File(dir, "weekly_pace.png"),
                    "קצב ממוצע שבועי – ${WorkoutType.from(mainType).label} (דק'/ק\"מ)",
                    weeks.map { it.format(DAY_FMT) },
                    paces, "קצב", null, null
                ) { Format.pace(it) }
            }
        }

        val body = buildString {
            appendLine("סטטיסטיקה שבועית: ${start.format(DAY_FMT)} – ${today.format(DAY_FMT)}")
            appendLine()
            if (goalKm > 0) {
                val done = thisWeek.sumOf { it.distanceM } / 1000
                val pct = (done / goalKm * 100).roundToInt()
                appendLine(
                    if (done >= goalKm) "🎯 יעד שבועי: הושג! ${String.format(Locale.US, "%.1f", done)} מתוך ${String.format(Locale.US, "%.1f", goalKm)} ק\"מ ($pct%)"
                    else "🎯 יעד שבועי: ${String.format(Locale.US, "%.1f", done)} מתוך ${String.format(Locale.US, "%.1f", goalKm)} ק\"מ ($pct%)"
                )
            }
            val weekRecords = thisWeek.flatMap { w -> w.records.map { it to w } }
            if (weekRecords.isNotEmpty()) {
                appendLine("🏆 שיאים אישיים השבוע: " + weekRecords.joinToString(", ") { (k, w) ->
                    "${k.label} ${WorkoutType.from(w.type).label} – ${k.display(w)}"
                })
            }
            appendLine()
            appendLine(summaryLine("אימונים", thisWeek.size.toDouble(), lastWeek.size.toDouble()) { it.roundToInt().toString() })
            appendLine(summaryLine("מרחק (ק\"מ)", thisWeek.sumOf { it.distanceM } / 1000, lastWeek.sumOf { it.distanceM } / 1000) {
                String.format(Locale.US, "%.2f", it)
            })
            appendLine(summaryLine("זמן", thisWeek.sumOf { it.durationMs }.toDouble(), lastWeek.sumOf { it.durationMs }.toDouble()) {
                Format.duration(it.toLong())
            })
            appendLine(summaryLine("קלוריות", thisWeek.sumOf { it.calories }, lastWeek.sumOf { it.calories }) { it.roundToInt().toString() })
            appendLine(summaryLine("טיפוס (מ')", thisWeek.sumOf { it.elevationGainM }, lastWeek.sumOf { it.elevationGainM }) {
                it.roundToInt().toString()
            })
            for (type in WorkoutType.entries) {
                val cur = thisWeek.filter { it.type == type.name }
                val prev = lastWeek.filter { it.type == type.name }
                if (cur.isEmpty() && prev.isEmpty()) continue
                val curPace = Format.avgPace(cur.sumOf { it.durationMs }, cur.sumOf { it.distanceM })
                val prevPace = Format.avgPace(prev.sumOf { it.durationMs }, prev.sumOf { it.distanceM })
                append("קצב ממוצע ${type.label}: ${Format.pace(curPace)}")
                if (prevPace != null) append(" (שבוע קודם ${Format.pace(prevPace)})")
                if (curPace != null && prevPace != null) {
                    val pct = (prevPace - curPace) / prevPace * 100
                    append(if (pct >= 0) " – מהיר ב-${pctText(pct)}" else " – איטי ב-${pctText(-pct)}")
                }
                appendLine()
            }
            if (thisWeek.isNotEmpty()) {
                appendLine()
                appendLine("האימונים השבוע:")
                thisWeek.forEach { appendLine("• ${workoutLine(it)}") }
                val best = thisWeek.filter { it.distanceM >= 1000 }
                    .minByOrNull { it.durationMs / it.distanceM }
                if (best != null) {
                    appendLine()
                    appendLine("הקצב הטוב השבוע: ${Format.pace(Format.avgPace(best.durationMs, best.distanceM))} ב-${Format.date(best.startTime)}")
                }
            }
            appendLine()
            appendLine("נשלח מ-WalkRun.")
        }
        Email("WalkRun – סטטיסטיקה שבועית ${start.format(DAY_FMT)}–${today.format(DAY_FMT)}", body, images)
    }

    // ---------------- Improvement ----------------

    suspend fun improvement(context: Context, app: WalkRunApp, w: WorkoutEntity): Email = withContext(Dispatchers.IO) {
        val prev = w.comparedToId?.let { app.workouts.getWorkout(it) }
        val images = mutableListOf<File>()
        val cur = app.workouts.splits(w.id, SplitUnit.KM)
        val old = prev?.let { app.workouts.splits(it.id, SplitUnit.KM) } ?: emptyList()
        if (cur.isNotEmpty()) {
            val n = maxOf(cur.size, old.size)
            images += ChartImages.paceLines(
                File(reportDir(context), "improved_${w.id}.png"),
                "קצב לכל ק\"מ (דק'/ק\"מ)",
                (1..n).map { it.toString() },
                (1..n).map { i -> cur.firstOrNull { it.index == i }?.pace },
                "האימון הזה",
                if (prev != null) (1..n).map { i -> old.firstOrNull { it.index == i }?.pace } else null,
                prev?.let { "קודם (${Format.date(it.startTime).take(5)})" }
            ) { Format.pace(it) }
        }
        val body = buildString {
            val headline = if (w.records.isNotEmpty()) "שיא אישי חדש!" else "שיפור!"
            appendLine("$headline ${w.title?.let { "$it · " } ?: ""}${WorkoutType.from(w.type).label} ב-${Format.date(w.startTime)}")
            appendLine()
            if (w.records.isNotEmpty()) {
                w.records.forEach { appendLine("🏆 ${it.label}: ${it.display(w)}") }
                appendLine()
            }
            w.trendText?.let { appendLine(it); appendLine() }
            appendLine("האימון: ${workoutLine(w)}")
            prev?.let { appendLine("הקודם: ${workoutLine(it)}") }
            if (cur.isNotEmpty()) {
                appendLine()
                appendLine("קצב לכל ק\"מ:")
                cur.forEach { s ->
                    val o = old.firstOrNull { it.index == s.index }
                    append("ק\"מ ${s.index}${if (s.fraction < 1) " (חלקי)" else ""}: ${Format.pace(s.pace)}")
                    if (o != null) append(" (קודם ${Format.pace(o.pace)})")
                    appendLine()
                }
            }
            appendLine()
            appendLine("נשלח מ-WalkRun.")
        }
        val what = if (w.records.isNotEmpty()) "שיא אישי" else "שיפור"
        Email("WalkRun – $what ב${WorkoutType.from(w.type).label} ${Format.date(w.startTime).take(10)}", body, images)
    }

    // ---------------- Delivery ----------------

    /** From the settings screen: opens the email app now. */
    suspend fun openWeeklyEmail(context: Context, app: WalkRunApp) {
        val email = weekly(context, app)
        if (email == null) {
            Toast.makeText(context, "אין אימונים בשבועיים האחרונים.", Toast.LENGTH_LONG).show()
            return
        }
        context.startActivity(emailIntent(context, app, email))
    }

    /** From the weekly alarm: a notification. A tap opens the email. */
    suspend fun notifyWeekly(context: Context, app: WalkRunApp) {
        val email = weekly(context, app) ?: return
        notify(context, NOTIFY_WEEKLY, "הסטטיסטיקה השבועית מוכנה", "לחץ כדי לשלוח אותה למייל.", emailIntent(context, app, email))
    }

    suspend fun notifyImprovement(context: Context, app: WalkRunApp, w: WorkoutEntity) {
        val email = improvement(context, app, w)
        notify(context, NOTIFY_IMPROVED, if (w.records.isNotEmpty()) "🏆 שיא אישי חדש!" else "כל הכבוד, שיפור!","לחץ כדי לשלוח את הדוח והגרף למייל.", emailIntent(context, app, email))
    }

    private suspend fun emailIntent(context: Context, app: WalkRunApp, email: Email): Intent {
        val to = app.settings.account.first()?.email?.takeIf { it != Account.GUEST.email && it.contains('@') }
        val uris = ArrayList<Uri>(email.images.map {
            FileProvider.getUriForFile(context, "${context.packageName}.files", it)
        })
        val send = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = if (uris.isEmpty()) "text/plain" else "image/png"
            to?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            putExtra(Intent.EXTRA_SUBJECT, email.subject)
            putExtra(Intent.EXTRA_TEXT, email.body)
            when (uris.size) {
                0 -> Unit
                1 -> putExtra(Intent.EXTRA_STREAM, uris[0])
                else -> putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            if (uris.isNotEmpty()) {
                clipData = ClipData.newUri(context.contentResolver, "report", uris[0]).also { c ->
                    uris.drop(1).forEach { c.addItem(ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Gmail directly when it is installed; else the user picks an app.
        val gmail = Intent(send).setPackage(GMAIL)
        return if (gmail.resolveActivity(context.packageManager) != null) gmail
        else Intent.createChooser(send, "שליחת הדוח").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun notify(context: Context, id: Int, title: String, text: String, open: Intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, WalkRunApp.REPORT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .addAction(0, "פתיחת האפליקציה", PendingIntent.getActivity(
                context, id + 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    // ---------------- Text ----------------

    private fun reportDir(context: Context) = File(context.cacheDir, "reports").apply { mkdirs() }

    private fun localDate(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun workoutLine(w: WorkoutEntity) =
        "${WorkoutType.from(w.type).label} ${Format.date(w.startTime)} · ${Format.km(w.distanceM)} ק\"מ · " +
            "${Format.duration(w.durationMs)} · קצב ${Format.pace(Format.avgPace(w.durationMs, w.distanceM))}"

    private fun summaryLine(name: String, cur: Double, prev: Double, fmt: (Double) -> String): String {
        val change = if (prev > 0) {
            val pct = (cur - prev) / prev * 100
            " (${if (pct >= 0) "+" else "−"}${pctText(kotlin.math.abs(pct))})"
        } else ""
        return "$name: ${fmt(cur)}  ·  שבוע קודם: ${fmt(prev)}$change"
    }

    private fun pctText(p: Double) = "${p.roundToInt()}%"
}
