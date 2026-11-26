package com.example.walkrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.Analysis
import com.example.walkrun.data.ReportSettings
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val MONTHS = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)
private val WEEKDAYS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

/** Calendar of workout days, streaks, and the monthly trend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(app: WalkRunApp, onBack: () -> Unit) {
    val workouts by app.workouts.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val reports by app.settings.reports.collectAsStateWithLifecycle(initialValue = ReportSettings())
    var month by remember { mutableStateOf(YearMonth.now()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("לוח שנה וסטטיסטיקה") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val s = Analysis.streaks(workouts, reports.weeklyGoalKm)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StreakItem("🔥 ${s.currentDays}", "ימים ברצף")
                    StreakItem("${s.longestDays}", "הרצף הארוך")
                    s.goalWeeks?.let { StreakItem("🎯 $it", "שבועות ביעד") }
                }
            }

            val byDay = Analysis.kmByDay(workouts)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("→") }
                        Text(
                            "${MONTHS[month.monthValue - 1]} ${month.year}",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { month = month.plusMonths(1) },
                            enabled = month < YearMonth.now()
                        ) { Text("←") }
                    }
                    MonthGrid(month, byDay)
                    val monthKm = byDay.filterKeys { YearMonth.from(it) == month }.values.sum()
                    val monthDays = byDay.keys.count { YearMonth.from(it) == month }
                    Text(
                        String.format(Locale.US, "%d ימי אימון · %.1f ק\"מ", monthDays, monthKm),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            MonthlyCard(Analysis.monthly(workouts), reports.monthlyGoalKm)
        }
    }
}

@Composable
private fun StreakItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Sunday-first month grid. A workout day is filled; the number under it is km. */
@Composable
private fun MonthGrid(month: YearMonth, byDay: Map<LocalDate, Double>) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday = 0
    val cells = lead + month.lengthOfMonth()
    val today = LocalDate.now()
    val filled = MaterialTheme.colorScheme.primary
    val onFilled = MaterialTheme.colorScheme.onPrimary
    Row(Modifier.fillMaxWidth()) {
        WEEKDAYS.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    for (row in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0..6) {
                val n = row * 7 + col - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (n in 1..month.lengthOfMonth()) {
                        val date = month.atDay(n)
                        val km = byDay[date]
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (km != null) filled else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$n",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (km != null) onFilled else MaterialTheme.colorScheme.onSurface
                                )
                                km?.let {
                                    Text(
                                        String.format(Locale.US, "%.1f", it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onFilled
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
