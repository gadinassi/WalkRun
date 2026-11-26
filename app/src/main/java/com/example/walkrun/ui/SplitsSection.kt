package com.example.walkrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.HistoryPeriod
import com.example.walkrun.data.SplitRow
import com.example.walkrun.data.SplitUnit
import com.example.walkrun.data.Splits
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.util.Format
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val BaselineGray = Color(0xFF8A94A6)
private val FasterGreen = Color(0xFF2E7D32)
private val SlowerRed = Color(0xFFC62828)

private data class Comparison(
    val rows: List<SplitRow>,
    val historyCount: Int,
    /** Average pace of the history workouts together (total time / total distance). */
    val historyPace: Double?
)

/** Compares this workout, split by split, with the average of earlier workouts of the same type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitsSection(app: WalkRunApp, workout: WorkoutEntity) {
    var unit by rememberSaveable { mutableStateOf(SplitUnit.KM) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
    var data by remember { mutableStateOf<Comparison?>(null) }

    LaunchedEffect(workout.id, unit, period) {
        data = null
        val current = app.workouts.splits(workout.id, unit)
        // The period is counted back from this workout, so an old summary shows its own history.
        val from = period.days?.let { workout.startTime - it * 86_400_000L } ?: 0L
        val history = app.workouts.history(workout.type, from, workout.startTime)
        val historySplits = history.map { app.workouts.splits(it.id, unit) }
        val valid = history.filter { it.distanceM >= 200 }
        val historyPace = if (valid.isEmpty()) null
        else valid.sumOf { it.durationMs } / 1000.0 / (valid.sumOf { it.distanceM } / 1000.0)
        data = Comparison(Splits.compare(current, historySplits), history.size, historyPace)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("השוואה להיסטוריה", fontWeight = FontWeight.Bold)
            ChipRow(SplitUnit.entries, unit, { it.label }) { unit = it }
            ChipRow(HistoryPeriod.entries, period, { it.label }) { period = it }

            val d = data
            when {
                d == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                d.rows.isEmpty() -> Text(
                    if (unit == SplitUnit.KM) "הפעילות קצרה מדי לחלוקה לקילומטרים."
                    else "הפעילות קצרה מדי לחלוקה ל-10 דקות."
                )
                else -> {
                    val typeName = if (WorkoutType.from(workout.type) == WorkoutType.RUN) "ריצות" else "הליכות"
                    HeaderLine(workout, d, typeName, period)
                    PaceChart(d.rows, unit)
                    ChangeBars(d.rows, unit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) })
        }
    }
}

@Composable
private fun HeaderLine(workout: WorkoutEntity, d: Comparison, typeName: String, period: HistoryPeriod) {
    val when_ = when (period) {
        HistoryPeriod.WEEK -> "בשבוע האחרון"
        HistoryPeriod.MONTH -> "בחודש האחרון"
        HistoryPeriod.ALL -> "בכלל"
    }
    if (d.historyCount == 0 || d.historyPace == null) {
        Text("אין $typeName קודמות $when_. הגרף מציג רק את הפעילות הזו.")
        return
    }
    val pace = Format.avgPace(workout.durationMs, workout.distanceM)
    Text("קצב ממוצע ${Format.pace(pace)} מול ${Format.pace(d.historyPace)} (ממוצע ${d.historyCount} $typeName $when_)")
    if (pace != null) {
        val (text, color) = changeLabel((d.historyPace - pace) / d.historyPace * 100.0)
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

private fun changeLabel(pct: Double): Pair<String, Color> = when {
    pct >= 2 -> "▲ מהיר ב-${pctText(pct)}" to FasterGreen
    pct <= -2 -> "▼ איטי ב-${pctText(pct)}" to SlowerRed
    else -> "≈ ללא שינוי משמעותי" to BaselineGray
}

private fun pctText(pct: Double) = String.format(Locale.US, "%.0f%%", abs(pct))

private fun splitLabel(row: SplitRow, unit: SplitUnit): String {
    val partial = row.fraction < 0.999
    return if (unit == SplitUnit.KM) {
        if (partial) "ק\"מ ${row.index} (${String.format(Locale.US, "%.1f", row.fraction)})" else "ק\"מ ${row.index}"
    } else {
        val endMin = ((row.index - 1) + row.fraction) * 10
        "${(row.index - 1) * 10}–${endMin.toInt()} ד'"
    }
}

// ---------------- Pace line chart ----------------

/**
 * Pace per split: this workout (dark blue, solid) and the history average (gray, dashed).
 * Faster is higher. The x axis goes left to right in time order, like a usual chart.
 */
@Composable
private fun PaceChart(rows: List<SplitRow>, unit: SplitUnit) {
    val paces = rows.map { it.pace } + rows.mapNotNull { it.baselinePace }
    val fastest = paces.min()
    val slowest = paces.max()
    // Leave 10% room above and below, and at least 30 s/km of range.
    val range = max(slowest - fastest, 30.0)
    val top = fastest - range * 0.1
    val bottom = slowest + range * 0.1
    // Theme colors: dark blue on the light theme, light blue on the dark theme.
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(lineColor); Text("פעילות זו", style = MaterialTheme.typography.bodySmall)
        LegendDot(BaselineGray); Text("ממוצע התקופה", style = MaterialTheme.typography.bodySmall)
    }
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = if (rows.size == 1) w / 2 else w * i / (rows.size - 1)
            fun y(pace: Double) = ((pace - top) / (bottom - top) * h).toFloat()

            // Horizontal grid lines
            for (k in 0..3) {
                val gy = h * k / 3
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // History average: dashed gray line through the splits that have a value.
            val base = Path()
            var started = false
            rows.forEachIndexed { i, r ->
                val b = r.baselinePace ?: run { started = false; return@forEachIndexed }
                if (!started) base.moveTo(x(i), y(b)) else base.lineTo(x(i), y(b))
                started = true
                drawCircle(BaselineGray, 5f, Offset(x(i), y(b)))
            }
            drawPath(
                base, BaselineGray,
                style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            )
            // This workout
            val cur = Path()
            rows.forEachIndexed { i, r -> if (i == 0) cur.moveTo(x(i), y(r.pace)) else cur.lineTo(x(i), y(r.pace)) }
            drawPath(cur, lineColor, style = Stroke(width = 6f))
            rows.forEachIndexed { i, r -> drawCircle(lineColor, 8f, Offset(x(i), y(r.pace))) }
        }
        // Axis labels: fastest at the top, slowest at the bottom (absolute left, also in RTL).
        Text(
            Format.pace(top.coerceAtLeast(1.0)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).padding(start = 10.dp)
        )
        Text(
            Format.pace(bottom),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(AbsoluteAlignment.BottomLeft).padding(start = 10.dp)
        )
    }
    Box(Modifier.fillMaxWidth()) {
        Text(
            splitLabel(rows.first(), unit),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
        )
        if (rows.size > 1) {
            Text(
                splitLabel(rows.last(), unit),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(AbsoluteAlignment.CenterRight)
            )
        }
    }
    Text("למעלה = מהיר יותר", style = MaterialTheme.typography.labelSmall, color = BaselineGray)
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

// ---------------- Change per split ----------------

/** One row per split: label, pace, a bar of the change (green = faster, red = slower), percent. */
@Composable
private fun ChangeBars(rows: List<SplitRow>, unit: SplitUnit) {
    val maxAbs = max(10.0, rows.mapNotNull { it.changePct?.let(::abs) }.maxOrNull() ?: 0.0).coerceAtMost(50.0)
    Spacer(Modifier.height(4.dp))
    Text("שינוי בכל חלק מול ממוצע התקופה", fontWeight = FontWeight.Bold)
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(splitLabel(r, unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp))
            Text(Format.pace(r.pace), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
            val pct = r.changePct
            val axis = MaterialTheme.colorScheme.outline
            Canvas(Modifier.weight(1f).height(18.dp).padding(horizontal = 6.dp)) {
                val mid = size.width / 2
                drawLine(axis, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 2f)
                if (pct != null && abs(pct) >= 0.5) {
                    val len = (abs(pct).coerceAtMost(maxAbs) / maxAbs * mid).toFloat()
                    // Faster grows to the right, slower to the left (absolute, also in RTL).
                    val left = if (pct > 0) mid else mid - len
                    drawRect(
                        if (pct > 0) FasterGreen else SlowerRed,
                        Offset(left, size.height * 0.15f),
                        Size(len, size.height * 0.7f)
                    )
                }
            }
            Text(
                when {
                    pct == null -> "—"
                    pct >= 0.5 -> "+" + pctText(pct)
                    pct <= -0.5 -> "-" + pctText(pct)
                    else -> "0%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    pct == null -> BaselineGray
                    pct >= 2 -> FasterGreen
                    pct <= -2 -> SlowerRed
                    else -> BaselineGray
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(48.dp)
            )
        }
    }
    Text(
        "ירוק = מהיר מהממוצע, אדום = איטי מהממוצע. — = אין נתונים לחלק הזה בתקופה.",
        style = MaterialTheme.typography.labelSmall,
        color = BaselineGray
    )
}
