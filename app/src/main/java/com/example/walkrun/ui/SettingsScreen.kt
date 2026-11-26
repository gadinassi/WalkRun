package com.example.walkrun.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.walkrun.WalkRunApp
import com.example.walkrun.auth.GoogleAuth
import com.example.walkrun.auth.SignInCancelledException
import com.example.walkrun.data.Account
import com.example.walkrun.data.AnnounceSettings
import com.example.walkrun.data.Sex
import com.example.walkrun.data.UserProfile
import com.example.walkrun.tracking.TrackingStatus
import com.example.walkrun.tracking.TrackingStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: WalkRunApp, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val account by app.settings.account.collectAsStateWithLifecycle(initialValue = null)
    var signingIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    var loaded by remember { mutableStateOf(false) }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf(LocalDate.now()) }
    var pickBirth by remember { mutableStateOf(false) }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var annEnabled by remember { mutableStateOf(true) }
    var annByDistance by remember { mutableStateOf(true) }
    var annByTime by remember { mutableStateOf(false) }
    var annDistance by remember { mutableStateOf("") }
    var annTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val p = app.settings.profile.first()
        val a = app.settings.announce.first()
        height = p.heightCm.toString()
        weight = p.weightKg.toString()
        birth = p.birthDate
        sex = p.sex
        annEnabled = a.enabled
        annByDistance = a.byDistance
        annByTime = a.byTime
        annDistance = a.distanceKm.toString()
        annTime = a.timeMin.toString()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
                navigationIcon = { TextButton(onClick = onBack) { Text("חזרה") } }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("פרטים אישיים", style = MaterialTheme.typography.titleMedium)
            NumberField("גובה (ס\"מ)", height) { height = it }
            NumberField("משקל (ק\"ג)", weight, decimal = true) { weight = it }
            // Read-only field. A tap opens the calendar.
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = birth.format(BIRTH_FORMAT),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תאריך לידה") },
                    supportingText = { Text("גיל: ${Period.between(birth, LocalDate.now()).years}") },
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.matchParentSize().clickable { pickBirth = true })
            }
            Text("מין")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = sex == Sex.MALE, onClick = { sex = Sex.MALE })
                Text("זכר")
                Spacer(Modifier.width(24.dp))
                RadioButton(selected = sex == Sex.FEMALE, onClick = { sex = Sex.FEMALE })
                Text("נקבה")
            }

            HorizontalDivider()

            Text("הודעות קוליות", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("הפעלה", modifier = Modifier.weight(1f))
                Switch(checked = annEnabled, onCheckedChange = { annEnabled = it })
            }
            // Distance and time are independent. Both can be on together.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = annByDistance, onCheckedChange = { annByDistance = it }, enabled = annEnabled)
                Text("לפי מרחק")
            }
            if (annByDistance) {
                NumberField("כל כמה ק\"מ", annDistance, decimal = true) { annDistance = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = annByTime, onCheckedChange = { annByTime = it }, enabled = annEnabled)
                Text("לפי זמן")
            }
            if (annByTime) {
                NumberField("כל כמה דקות", annTime) { annTime = it }
            }

            Button(onClick = {
                val h = height.toIntOrNull()
                val w = weight.toDoubleOrNull()
                val a = Period.between(birth, LocalDate.now()).years
                val d = annDistance.toDoubleOrNull()
                val t = annTime.toIntOrNull()
                // A hidden (unchecked) field is not validated. Its last valid value is kept.
                val error = when {
                    h == null || h !in 100..230 -> "גובה: ערך בין 100 ל-230 ס\"מ."
                    w == null || w !in 30.0..250.0 -> "משקל: ערך בין 30 ל-250 ק\"ג."
                    a !in 10..100 -> "תאריך לידה: הגיל צריך להיות בין 10 ל-100."
                    annEnabled && !annByDistance && !annByTime -> "הודעות קוליות: בחר מרחק, זמן או שניהם."
                    annByDistance && (d == null || d !in 0.1..50.0) -> "מרחק הודעה: ערך בין 0.1 ל-50 ק\"מ."
                    annByTime && (t == null || t !in 1..120) -> "זמן הודעה: ערך בין 1 ל-120 דקות."
                    else -> null
                }
                if (error != null) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    return@Button
                }
                scope.launch {
                    app.settings.saveProfile(UserProfile(h!!, w!!, birth, sex))
                    val defaults = AnnounceSettings()
                    app.settings.saveAnnounce(
                        AnnounceSettings(
                            enabled = annEnabled,
                            byDistance = annByDistance,
                            byTime = annByTime,
                            distanceKm = d?.takeIf { it in 0.1..50.0 } ?: defaults.distanceKm,
                            timeMin = t?.takeIf { it in 1..120 } ?: defaults.timeMin
                        )
                    )
                    Toast.makeText(context, "ההגדרות נשמרו.", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("שמירה") }

            Text(
                "שינוי בהגדרות ההודעות משפיע על האימון הבא, לא על אימון פעיל.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text("חשבון", style = MaterialTheme.typography.titleMedium)
            val isGuest = account == null || account == Account.GUEST
            Text(
                if (isGuest) "לא מחובר. הנתונים נשמרים במכשיר."
                else "מחובר כ: ${account?.name} (${account?.email})"
            )
            // A guest can always connect a Google account later. The saved workouts stay.
            if (isGuest) {
                Button(
                    enabled = !signingIn,
                    onClick = {
                        signingIn = true
                        signInError = null
                        scope.launch {
                            val result = GoogleAuth.signIn(context)
                            val signedIn = result.getOrNull()
                            if (signedIn != null) {
                                app.settings.saveAccount(signedIn)
                                Toast.makeText(context, "התחברת כ-${signedIn.name}.", Toast.LENGTH_SHORT).show()
                            } else {
                                val e = result.exceptionOrNull()
                                signInError = if (e is SignInCancelledException) null
                                else e?.message ?: "ההתחברות נכשלה."
                            }
                            signingIn = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (signingIn) "מתחבר..." else "התחברות עם Google") }
                signInError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            // Signing out during a workout would leave the recording running without a user.
            val workoutActive = tracking.status != TrackingStatus.IDLE
            if (!isGuest) OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { GoogleAuth.signOut(context) }
                        app.settings.saveAccount(null)
                        onSignedOut()
                    }
                },
                enabled = !workoutActive,
                modifier = Modifier.fillMaxWidth()
            ) { Text("התנתקות") }
            if (workoutActive && !isGuest) {
                Text("יש לסיים את האימון הפעיל לפני התנתקות.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (pickBirth) {
        // The date picker works in UTC milliseconds at midnight.
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birth.toEpochDay() * DAY_MS,
            yearRange = (today.year - 100)..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toEpochDay() * DAY_MS
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickBirth = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { birth = LocalDate.ofEpochDay(it / DAY_MS) }
                    pickBirth = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { pickBirth = false }) { Text("ביטול") } }
        ) {
            DatePicker(state = state, title = { Text("תאריך לידה", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private const val DAY_MS = 86_400_000L
private val BIRTH_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
