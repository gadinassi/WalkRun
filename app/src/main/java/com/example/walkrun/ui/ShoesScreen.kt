package com.example.walkrun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.ShoeEntity
import com.example.walkrun.data.ShoeWear
import kotlinx.coroutines.launch
import java.util.Locale

/** Color of the wear bar: normal, orange from 90%, red at the limit. */
@Composable
fun wearColor(used: Double): Color = when {
    used >= 1.0 -> StopRed
    used >= 0.9 -> Color(0xFFEF6C00)
    else -> MaterialTheme.colorScheme.primary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoesScreen(app: WalkRunApp, onBack: () -> Unit) {
    val shoes by app.workouts.shoes.collectAsStateWithLifecycle(initialValue = emptyList())
    val totals by app.workouts.shoeTotals.collectAsStateWithLifecycle(initialValue = emptyMap())
    val current by app.settings.currentShoe.collectAsStateWithLifecycle(initialValue = 0L)
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<ShoeEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<ShoeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("נעליים") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "בחר את הנעליים שבהן אתה מתאמן. כל אימון חדש נרשם עליהן, ותקבל תזכורת כשיגיעו למגבלה.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { edit = ShoeEntity(name = "") }, modifier = Modifier.fillMaxWidth()) { Text("הוספת נעליים") }
            shoes.forEach { s ->
                val meters = totals[s.id] ?: 0.0
                val used = ShoeWear.used(meters, s.limitKm)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = if (s.retired) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    else CardDefaults.cardColors()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!s.retired) {
                                RadioButton(
                                    selected = current == s.id,
                                    onClick = { scope.launch { app.settings.setCurrentShoe(s.id) } }
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.name + if (s.retired) " (בדימוס)" else "", fontWeight = FontWeight.Bold)
                                Text(
                                    String.format(Locale.US, "%.1f / %.0f ק\"מ", meters / 1000, s.limitKm),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { edit = s }) { Text("עריכה") }
                        }
                        LinearProgressIndicator(
                            progress = { used.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = wearColor(used),
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {}
                        )
                        if (used >= 1.0 && !s.retired) {
                            Text("עברו את המגבלה. כדאי להחליף.", color = StopRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    edit?.let { s ->
        var name by remember(s) { mutableStateOf(s.name) }
        var limit by remember(s) { mutableStateOf(s.limitKm.toInt().toString()) }
        var retired by remember(s) { mutableStateOf(s.retired) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (s.id == 0L) "נעליים חדשות" else "עריכת נעליים") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                        label = { Text("שם") }, placeholder = { Text("לדוגמה: Pegasus כחולות") }
                    )
                    NumberField("החלפה אחרי (ק\"מ)", limit, step = 50.0, min = 50.0, max = 3000.0) { limit = it }
                    if (s.id != 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = retired, onCheckedChange = { retired = it })
                            Text("בדימוס (לא בשימוש)")
                        }
                        TextButton(onClick = { confirmDelete = s; edit = null }) { Text("מחיקה", color = StopRed) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (limit.toDoubleOrNull() ?: 0.0) >= 50,
                    onClick = {
                        val updated = s.copy(name = name.trim(), limitKm = limit.toDouble(), retired = retired)
                        edit = null
                        scope.launch {
                            if (s.id == 0L) {
                                val id = app.workouts.addShoe(updated)
                                // The first pair becomes the current one.
                                if (app.workouts.getShoe(current) == null) app.settings.setCurrentShoe(id)
                            } else {
                                app.workouts.updateShoe(updated)
                                if (updated.retired && current == s.id) app.settings.setCurrentShoe(0L)
                            }
                        }
                    }
                ) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("ביטול") } }
        )
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("למחוק את \"${s.name}\"?") },
            text = { Text("האימונים לא נמחקים. רק הקישור שלהם לנעליים האלה יוסר.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        app.workouts.deleteShoe(s.id)
                        if (current == s.id) app.settings.setCurrentShoe(0L)
                    }
                }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("ביטול") } }
        )
    }
}

/** On the summary screen: the shoes of this workout, with a tap to change them. */
@Composable
fun WorkoutShoeRow(app: WalkRunApp, workoutId: Long, shoeId: Long?, onChanged: () -> Unit) {
    val shoes by app.workouts.shoes.collectAsStateWithLifecycle(initialValue = emptyList())
    if (shoes.isEmpty()) return
    var pick by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val name = shoes.firstOrNull { it.id == shoeId }?.name ?: "לא נבחרו"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("👟 נעליים: $name", modifier = Modifier.weight(1f))
        TextButton(onClick = { pick = true }) { Text("שינוי") }
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text("באילו נעליים?") },
            text = {
                Column {
                    (listOf<ShoeEntity?>(null) + shoes).forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = s?.id == shoeId,
                                onClick = {
                                    pick = false
                                    scope.launch { app.workouts.setWorkoutShoe(workoutId, s?.id); onChanged() }
                                }
                            )
                            Text(s?.name ?: "ללא")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("סגירה") } }
        )
    }
}
