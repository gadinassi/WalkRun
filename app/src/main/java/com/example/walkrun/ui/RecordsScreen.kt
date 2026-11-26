package com.example.walkrun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.RecordBook
import com.example.walkrun.data.RecordKind
import com.example.walkrun.data.WorkoutType
import com.example.walkrun.util.Format

/** Personal records per workout type. A tap on a record opens its workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(app: WalkRunApp, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val workouts by app.workouts.all.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("שיאים אישיים") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val byType = WorkoutType.entries.associateWith { t -> workouts.filter { it.type == t.name } }
            if (byType.values.all { it.isEmpty() }) Text("אין עדיין פעילויות.")
            for ((type, list) in byType) {
                if (list.isEmpty()) continue
                val records = RecordBook.of(list)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆 ${type.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        records.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenWorkout(r.workout.id) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.kind.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(r.kind.display(r.workout))
                                    Text(
                                        Format.date(r.workout.startTime),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        if (records.size < RecordKind.entries.size) {
                            Text(
                                "זמנים ל-5 ק\"מ, 10 ק\"מ וחצי מרתון יופיעו אחרי אימון באורך הזה.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Text(
                "זמן שיא הוא הזמן המהיר ביותר למרחק בכל מקום באימון, לא רק מההתחלה. עצירות לא נספרות.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
