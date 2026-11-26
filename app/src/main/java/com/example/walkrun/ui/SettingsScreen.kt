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
import com.example.walkrun.data.ReportSettings
import com.example.walkrun.data.Sex
import com.example.walkrun.report.Reports
import com.example.walkrun.report.GmailSender
import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    var minSaveKm by remember { mutableStateOf("") }
    var repWeekly by remember { mutableStateOf(true) }
    var repImprove by remember { mutableStateOf(true) }
    var goalKm by remember { mutableStateOf("") }
    var autoGmail by remember { mutableStateOf(false) }
    var gmailBusy by remember { mutableStateOf(false) }
    // The Google approval window for the gmail.send scope.
    val approval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        gmailBusy = false
        val auth = GmailSender.fromApprovalResult(context, res.data)
        if (res.resultCode == Activity.RESULT_OK && auth is GmailSender.Auth.Token) {
            autoGmail = true
            scope.launch { app.settings.setAutoGmail(true) }
            Toast.makeText(context, "השליחה האוטומטית הופעלה.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "האישור בוטל. הדוחות ייפתחו כטיוטה ב-Gmail.", Toast.LENGTH_LONG).show()
        }
    }

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
        val r = app.settings.reports.first()
        minSaveKm = r.minSaveKm.toString()
        repWeekly = r.weekly
        repImprove = r.onImprovement
        goalKm = if (r.weeklyGoalKm > 0) r.weeklyGoalKm.toString() else "0"
        autoGmail = r.autoGmail
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

            HorizontalDivider()

            Text("יעדים, היסטוריה ודוחות", style = MaterialTheme.typography.titleMedium)
            NumberField("יעד שבועי, ראשון עד שבת (ק\"מ, 0 = ללא יעד)", goalKm, decimal = true) { goalKm = it }
            NumberField("לא לשמור אימון קצר מ- (ק\"מ)", minSaveKm, decimal = true) { minSaveKm = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = repWeekly, onCheckedChange = { repWeekly = it })
                Text("דוח שבועי במייל (יום ראשון בבוקר)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = repImprove, onCheckedChange = { repImprove = it })
                Text("דוח במייל כשיש שיפור או שיא אישי")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("שליחה אוטומטית דרך Gmail")
                    Text(
                        if (autoGmail) "הדוחות נשלחים ישירות לכתובת שלך."
                        else "כבוי: הדוח נפתח כטיוטה ב-Gmail ושולחים ידנית.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = autoGmail,
                    enabled = !gmailBusy,
                    onCheckedChange = { on ->
                        if (!on) {
                            autoGmail = false
                            scope.launch { app.settings.setAutoGmail(false) }
                            return@Switch
                        }
                        val email = account?.email?.takeIf { it != Account.GUEST.email && it.contains('@') }
                        if (email == null) {
                            Toast.makeText(context, "יש להתחבר עם Google קודם.", Toast.LENGTH_LONG).show()
                            return@Switch
                        }
                        gmailBusy = true
                        scope.launch {
                            try {
                                when (val auth = GmailSender.authorize(context, email)) {
                                    is GmailSender.Auth.Token -> {
                                        gmailBusy = false
                                        autoGmail = true
                                        app.settings.setAutoGmail(true)
                                    }
                                    is GmailSender.Auth.NeedsApproval ->
                                        approval.launch(IntentSenderRequest.Builder(auth.intent).build())
                                }
                            } catch (e: Exception) {
                                gmailBusy = false
                                Log.w("GmailSender", "Authorize failed", e)
                                Toast.makeText(context, "ההרשאה נכשלה: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            OutlinedButton(
                onClick = { scope.launch { Reports.openWeeklyEmail(context, app) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("שליחת הדוח השבועי עכשיו") }

            Button(onClick = {
                val h = height.toIntOrNull()
                val w = weight.toDoubleOrNull()
                val a = Period.between(birth, LocalDate.now()).years
                val d = annDistance.toDoubleOrNull()
                val t = annTime.toIntOrNull()
                val m = minSaveKm.toDoubleOrNull()
                val g = goalKm.ifBlank { "0" }.toDoubleOrNull()
                // A hidden (unchecked) field is not validated. Its last valid value is kept.
                val error = when {
                    h == null || h !in 100..230 -> "גובה: ערך בין 100 ל-230 ס\"מ."
                    w == null || w !in 30.0..250.0 -> "משקל: ערך בין 30 ל-250 ק\"ג."
                    a !in 10..100 -> "תאריך לידה: הגיל צריך להיות בין 10 ל-100."
                    annEnabled && !annByDistance && !annByTime -> "הודעות קוליות: בחר מרחק, זמן או שניהם."
                    annByDistance && (d == null || d !in 0.1..50.0) -> "מרחק הודעה: ערך בין 0.1 ל-50 ק\"מ."
                    annByTime && (t == null || t !in 1..120) -> "זמן הודעה: ערך בין 1 ל-120 דקות."
                    m == null || m !in 0.0..20.0 -> "אימון קצר: ערך בין 0 ל-20 ק\"מ."
                    g == null || g !in 0.0..500.0 -> "יעד שבועי: ערך בין 0 ל-500 ק\"מ."
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
                    app.settings.saveReports(ReportSettings(m!!, repWeekly, repImprove, g!!, autoGmail))
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
                        app.settings.setAutoGmail(false)
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
