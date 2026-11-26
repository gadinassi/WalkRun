package com.example.walkrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.walkrun.data.Analysis
import com.example.walkrun.util.Format
import java.util.Locale
import kotlin.math.roundToInt

/** Altitude along the route. The x axis is distance, left to right. */
@Composable
fun ElevationCard(profile: List<Pair<Double, Double>>, gainM: Double) {
    if (profile.size < 3) return
    val minAlt = profile.minOf { it.second }
    val maxAlt = profile.maxOf { it.second }
    val range = (maxAlt - minAlt).coerceAtLeast(10.0)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val d0 = profile.first().first
    val d1 = profile.last().first.coerceAtLeast(d0 + 1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("גובה לאורך המסלול", fontWeight = FontWeight.Bold)
            Text(
                // The barometer gives correct differences but not a correct height above sea level.
                "הפרש גובה ${(maxAlt - minAlt).roundToInt()} מ' · טיפוס מצטבר ${gainM.roundToInt()} מ'",
                style = MaterialTheme.typography.bodySmall
            )
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val w = size.width
                    val h = size.height
                    fun x(d: Double) = ((d - d0) / (d1 - d0) * w).toFloat()
                    fun y(a: Double) = (h - (a - minAlt) / range * h * 0.9 - h * 0.05).toFloat()
                    val line = Path()
                    val area = Path().apply { moveTo(0f, h) }
                    profile.forEachIndexed { i, (d, a) ->
                        if (i == 0) line.moveTo(x(d), y(a)) else line.lineTo(x(d), y(a))
                        area.lineTo(x(d), y(a))
                    }
                    area.lineTo(x(profile.last().first), h)
                    area.close()
                    drawPath(area, fillColor)
                    drawPath(line, lineColor, style = Stroke(width = 4f))
                }
            }
            Box(Modifier.fillMaxWidth()) {
                Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(AbsoluteAlignment.CenterLeft))
                Text(
                    "${Format.km(d1 - d0)} ק\"מ",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(AbsoluteAlignment.CenterRight)
                )
            }
        }
    }
}

/** Legend under the pace heat map. */
@Composable
fun HeatLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "מהיר" to Analysis.heatColor(0.9, 1.0),
            "ממוצע" to Analysis.heatColor(1.0, 1.0),
            "איטי" to Analysis.heatColor(1.1, 1.0)
        ).forEach { (label, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("■", color = androidx.compose.ui.graphics.Color(c))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Bars: km per month. Line labels: average pace. Oldest month on the left. */
@Composable
fun MonthlyCard(months: List<Analysis.Month>, goalKm: Double = 0.0) {
    val barColor = MaterialTheme.colorScheme.primary
    val goalColor = StartGreen
    // The goal line must fit in the chart too.
    val maxKm = maxOf(months.maxOfOrNull { it.km } ?: 0.0, goalKm).coerceAtLeast(1.0)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("מגמה חודשית – ק\"מ בחודש", fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val group = size.width / months.size
                months.forEachIndexed { i, m ->
                    val h = (m.km / maxKm * size.height * 0.92).toFloat()
                    drawRect(
                        if (goalKm > 0 && m.km >= goalKm) goalColor else barColor,
                        Offset(group * i + group * 0.18f, size.height - h), Size(group * 0.64f, h)
                    )
                }
                if (goalKm > 0) {
                    val gy = (size.height - goalKm / maxKm * size.height * 0.92).toFloat()
                    drawLine(
                        goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }
            }
            if (goalKm > 0) {
                Text(
                    String.format(Locale.US, "קו ירוק = יעד חודשי %.0f ק\"מ. עמודה ירוקה = היעד הושג.", goalKm),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(Modifier.fillMaxWidth()) {
                // Absolute left-to-right order, also in RTL.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        months.forEach { m ->
                            Text(
                                m.month.monthValue.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            val cur = months.last()
            val prev = months.getOrNull(months.size - 2)
            Text(
                buildString {
                    append(String.format(Locale.US, "החודש: %.1f ק\"מ · %d אימונים", cur.km, cur.count))
                    cur.avgPace?.let { append(" · קצב ${Format.pace(it)}") }
                    if (prev != null && prev.km > 0) {
                        val pct = ((cur.km - prev.km) / prev.km * 100).roundToInt()
                        append(" · ${if (pct >= 0) "+" else ""}$pct% מהחודש הקודם")
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
