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
import com.example.walkrun.data.DisplaySettings
import com.example.walkrun.data.ThemeMode
import com.example.walkrun.data.TrainingSettings
import com.example.walkrun.data.Sex
import com.example.walkrun.report.Reports
import com.example.walkrun.report.GmailSender
import com.example.walkrun.report.HealthSync
import com.example.walkrun.report.DriveBackup
import com.example.walkrun.report.WalkRunWidget
import com.example.walkrun.report.GoogleAccess
import com.example.walkrun.util.Format
import com.example.walkrun.util.TimeText
import androidx.compose.material3.AlertDialog
import androidx.health.connect.client.PermissionController
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
fun SettingsScreen(app: WalkRunApp, onBack: () -> Unit, onSignedOut: () -> Unit, onShoes: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracking by TrackingStore.state.collectAsStateWithLifecycle()
    val account by app.settings.account.collectAsStateWithLifecycle(initialValue = null)
    val display by app.settings.display.collectAsStateWithLifecycle(initialValue = DisplaySettings())
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
    var duckMusic by remember { mutableStateOf(true) }
    var kmSummary by remember { mutableStateOf(true) }
    var minSaveKm by remember { mutableStateOf("") }
    var repWeekly by remember { mutableStateOf(true) }
    var repImprove by remember { mutableStateOf(true) }
    var goalKm by remember { mutableStateOf("") }
    var monthGoalKm by remember { mutableStateOf("") }
    var autoPause by remember { mutableStateOf(true) }
    var paceAlert by remember { mutableStateOf(false) }
    var slowPace by remember { mutableStateOf("") }
    var fastPace by remember { mutableStateOf("") }
    var intervalsOn by remember { mutableStateOf(false) }
    var workTime by remember { mutableStateOf("") }
    var restTime by remember { mutableStateOf("") }
    var repeats by remember { mutableStateOf("") }
    var autoGmail by remember { mutableStateOf(false) }
    val healthOn by app.settings.healthConnect.collectAsStateWithLifecycle(initialValue = false)
    val driveOn by app.settings.driveBackup.collectAsStateWithLifecycle(initialValue = false)
    val lastBackup by app.settings.lastBackup.collectAsStateWithLifecycle(initialValue = 0L)
    var driveBusy by remember { mutableStateOf<String?>(null) }
    var restoreInfo by remember { mutableStateOf<DriveBackup.Info?>(null) }
    // What to do after the Drive approval window: back up now (true) or only turn the switch on.
    var afterDriveApproval by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    val driveApproval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val auth = GoogleAccess.fromApprovalResult(context, res.data)
        val next = afterDriveApproval
        afterDriveApproval = null
        if (res.resultCode == Activity.RESULT_OK && auth is GoogleAccess.Auth.Token && next != null) {
            scope.launch { next(auth.value) }
        } else {
            driveBusy = null
            Toast.makeText(context, "האישור ל-Google Drive בוטל.", Toast.LENGTH_LONG).show()
        }
    }

    /** Gets a Drive token (approval window if needed), then runs [action]. */
    fun withDrive(busyText: String, action: suspend (String) -> Unit) {
        val email = account?.email?.takeIf { it != Account.GUEST.email && it.contains('@') }
        if (email == null) {
            Toast.makeText(context, "יש להתחבר עם Google קודם.", Toast.LENGTH_LONG).show()
            return
        }
        driveBusy = busyText
        val wrapped: suspend (String) -> Unit = { token ->
            try {
                action(token)
            } catch (e: Exception) {
                Log.w("DriveBackup", "Drive action failed", e)
                Toast.makeText(context, "הפעולה נכשלה: ${e.message?.take(120)}", Toast.LENGTH_LONG).show()
            } finally {
                driveBusy = null
            }
        }
        scope.launch {
            try {
                when (val auth = GoogleAccess.authorize(context, email, DriveBackup.SCOPE)) {
                    is GoogleAccess.Auth.Token -> wrapped(auth.value)
                    is GoogleAccess.Auth.NeedsApproval -> {
                        afterDriveApproval = wrapped
                        driveApproval.launch(IntentSenderRequest.Builder(auth.intent).build())
                    }
                }
            } catch (e: Exception) {
                driveBusy = null
                Log.w("DriveBackup", "Authorize failed", e)
                Toast.makeText(context, "ההרשאה נכשלה: ${e.message?.take(120)}", Toast.LENGTH_LONG).show()
            }
        }
    }
    // Health Connect permission screen. After approval, all saved workouts are written once.
    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthSync.PERMISSIONS)) {
            scope.launch {
                app.settings.setHealthConnect(true)
                val n = HealthSync.writeAll(app)
                Toast.makeText(context, "Health Connect מחובר. נכתבו $n אימונים.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "לא ניתנה הרשאה ל-Health Connect.", Toast.LENGTH_LONG).show()
        }
    }
    var gmailBusy by remember { mutableStateOf(false) }
    // The Google approval window for the gmail.send scope.
    val approval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        gmailBusy = false
        val auth = GoogleAccess.fromApprovalResult(context, res.data)
        if (res.resultCode == Activity.RESULT_OK && auth is GoogleAccess.Auth.Token) {
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
        duckMusic = a.duckMusic
        kmSummary = a.kmSummary
        val r = app.settings.reports.first()
        minSaveKm = r.minSaveKm.toString()
        repWeekly = r.weekly
        repImprove = r.onImprovement
        goalKm = if (r.weeklyGoalKm > 0) r.weeklyGoalKm.toString() else "0"
        monthGoalKm = if (r.monthlyGoalKm > 0) r.monthlyGoalKm.toString() else "0"
        autoGmail = r.autoGmail
        val tr = app.settings.training.first()
        autoPause = tr.autoPause
        paceAlert = tr.paceAlert
        slowPace = mmss(tr.slowPaceSec)
        fastPace = mmss(tr.fastPaceSec)
        intervalsOn = tr.intervals
        workTime = mmss(tr.workSec)
        restTime = mmss(tr.restSec)
        repeats = tr.repeats.toString()
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
            NumberField("גובה (ס\"מ)", height, min = 100.0, max = 230.0) { height = it }
            NumberField("משקל (ק\"ג)", weight, decimal = true, step = 0.5, min = 30.0, max = 250.0) { weight = it }
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

            // Display: applied at once, without the Save button.
            Text("תצוגה", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeMode.entries.forEach { m ->
                    RadioButton(
                        selected = display.theme == m,
                        onClick = { scope.launch { app.settings.saveDisplay(display.copy(theme = m)) } }
                    )
                    Text(m.label)
                    Spacer(Modifier.width(8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = display.keepScreenOn,
                    onCheckedChange = { on -> scope.launch { app.settings.saveDisplay(display.copy(keepScreenOn = on)) } }
                )
                Text("מסך דלוק בזמן אימון")
            }

            HorizontalDivider()

            Text("אימון", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onShoes, modifier = Modifier.fillMaxWidth()) { Text("👟 נעליים ומעקב בלאי ›") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoPause, onCheckedChange = { autoPause = it })
                Text("השהיה אוטומטית בעצירה (רמזור, מנוחה)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = paceAlert, onCheckedChange = { paceAlert = it })
                Text("התראת קצב")
            }
            if (paceAlert) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TimeField("איטי מ- (דק':שנ')", slowPace, stepSec = 5, minSec = 120, maxSec = 1800) { slowPace = it } }
                    Box(Modifier.weight(1f)) { TimeField("מהיר מ- (דק':שנ')", fastPace, stepSec = 5, minSec = 120, maxSec = 1800) { fastPace = it } }
                }
                Text("קצב לקילומטר. הודעה כשהקצב יוצא מהטווח, לכל היותר פעם בדקה.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = intervalsOn, onCheckedChange = { intervalsOn = it })
                Text("אינטרוולים (ריצה והליכה לסירוגין)")
            }
            if (intervalsOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TimeField("ריצה (דק':שנ')", workTime) { workTime = it } }
                    Box(Modifier.weight(1f)) { TimeField("הליכה (דק':שנ')", restTime) { restTime = it } }
                    Box(Modifier.weight(0.7f)) { NumberField("סבבים", repeats, min = 1.0, max = 50.0) { repeats = it } }
                }
                Text("האינטרוולים פועלים בכל אימון כל עוד האפשרות מסומנת.", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            HeartRateSettings(app)

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
                NumberField("כל כמה ק\"מ", annDistance, decimal = true, step = 0.5, min = 0.5, max = 50.0) { annDistance = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = annByTime, onCheckedChange = { annByTime = it }, enabled = annEnabled)
                Text("לפי זמן")
            }
            if (annByTime) {
                NumberField("כל כמה דקות", annTime, min = 1.0, max = 120.0) { annTime = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = kmSummary, onCheckedChange = { kmSummary = it }, enabled = annEnabled)
                Text("סיכום אחרי כל ק\"מ (זמן ושינוי מהק\"מ הקודם)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = duckMusic, onCheckedChange = { duckMusic = it })
                Text("הנמכת מוזיקה בזמן הודעה")
            }

            HorizontalDivider()

            Text("יעדים, היסטוריה ודוחות", style = MaterialTheme.typography.titleMedium)
            NumberField("יעד שבועי, ראשון עד שבת (ק\"מ, 0 = ללא יעד)", goalKm, decimal = true, step = 1.0, min = 0.0, max = 500.0) { goalKm = it }
            NumberField("יעד חודשי (ק\"מ, 0 = ללא יעד)", monthGoalKm, decimal = true, step = 5.0, min = 0.0, max = 2000.0) { monthGoalKm = it }
            NumberField("לא לשמור אימון קצר מ- (ק\"מ)", minSaveKm, decimal = true, step = 0.1, min = 0.0, max = 20.0) { minSaveKm = it }
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
                                    is GoogleAccess.Auth.Token -> {
                                        gmailBusy = false
                                        autoGmail = true
                                        app.settings.setAutoGmail(true)
                                    }
                                    is GoogleAccess.Auth.NeedsApproval ->
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("סנכרון ל-Health Connect")
                    Text(
                        "אימונים, מרחק, צעדים וקלוריות יופיעו גם ב-Google Fit ובאפליקציות בריאות.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = healthOn,
                    onCheckedChange = { on ->
                        if (!on) {
                            scope.launch { app.settings.setHealthConnect(false) }
                        } else if (!HealthSync.available(context)) {
                            Toast.makeText(context, "Health Connect לא זמין במכשיר.", Toast.LENGTH_LONG).show()
                        } else {
                            healthPermission.launch(HealthSync.PERMISSIONS)
                        }
                    }
                )
            }
            HorizontalDivider()

            Text("גיבוי ל-Google Drive", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("גיבוי אוטומטי כל לילה")
                    Text(
                        if (lastBackup > 0) "גיבוי אחרון: ${Format.date(lastBackup)}" else "עדיין אין גיבוי.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = driveOn,
                    enabled = driveBusy == null,
                    onCheckedChange = { on ->
                        if (!on) scope.launch { app.settings.setDriveBackup(false) }
                        else withDrive("מגבה...") { token ->
                            DriveBackup.backup(app, token)
                            app.settings.setDriveBackup(true)
                            Toast.makeText(context, "הגיבוי הופעל ונשמר גיבוי ראשון.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = driveBusy == null,
                    onClick = {
                        withDrive("מגבה...") { token ->
                            DriveBackup.backup(app, token)
                            Toast.makeText(context, "הגיבוי נשמר ב-Google Drive.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("גיבוי עכשיו") }
                OutlinedButton(
                    enabled = driveBusy == null && tracking.status == TrackingStatus.IDLE,
                    onClick = {
                        withDrive("בודק גיבוי...") { token ->
                            val info = DriveBackup.info(token)
                            if (info == null) Toast.makeText(context, "אין גיבוי ב-Google Drive.", Toast.LENGTH_LONG).show()
                            else restoreInfo = info
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("שחזור") }
            }
            driveBusy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "הגיבוי נשמר בתיקייה מוסתרת של האפליקציה ב-Drive. WalkRun לא רואה קבצים אחרים שלך.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

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
                val mg = monthGoalKm.ifBlank { "0" }.toDoubleOrNull()
                val slow = parseMmss(slowPace)
                val fast = parseMmss(fastPace)
                val work = parseMmss(workTime)
                val rest = parseMmss(restTime)
                val rep = repeats.toIntOrNull()
                // A hidden (unchecked) field is not validated. Its last valid value is kept.
                val error = when {
                    h == null || h !in 100..230 -> "גובה: ערך בין 100 ל-230 ס\"מ."
                    w == null || w !in 30.0..250.0 -> "משקל: ערך בין 30 ל-250 ק\"ג."
                    a !in 10..100 -> "תאריך לידה: הגיל צריך להיות בין 10 ל-100."
                    annEnabled && !annByDistance && !annByTime && !kmSummary ->
                        "הודעות קוליות: בחר מרחק, זמן או סיכום לכל ק\"מ."
                    annByDistance && (d == null || d !in 0.1..50.0) -> "מרחק הודעה: ערך בין 0.1 ל-50 ק\"מ."
                    annByTime && (t == null || t !in 1..120) -> "זמן הודעה: ערך בין 1 ל-120 דקות."
                    m == null || m !in 0.0..20.0 -> "אימון קצר: ערך בין 0 ל-20 ק\"מ."
                    g == null || g !in 0.0..500.0 -> "יעד שבועי: ערך בין 0 ל-500 ק\"מ."
                    mg == null || mg !in 0.0..2000.0 -> "יעד חודשי: ערך בין 0 ל-2000 ק\"מ."
                    paceAlert && (slow == null || fast == null || slow !in 120..1800 || fast !in 120..1800) ->
                        "התראת קצב: כתוב קצב בצורה 8:00, בין 2:00 ל-30:00."
                    paceAlert && fast!! >= slow!! -> "התראת קצב: הקצב המהיר צריך להיות קטן מהאיטי."
                    intervalsOn && (work == null || rest == null || work !in 10..3600 || rest !in 10..3600) ->
                        "אינטרוולים: זמנים בצורה 2:00, בין 0:10 ל-60:00."
                    intervalsOn && (rep == null || rep !in 1..50) -> "אינטרוולים: מספר סבבים בין 1 ל-50."
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
                            timeMin = t?.takeIf { it in 1..120 } ?: defaults.timeMin,
                            duckMusic = duckMusic,
                            kmSummary = kmSummary
                        )
                    )
                    val oldTr = app.settings.training.first()
                    app.settings.saveTraining(
                        TrainingSettings(
                            autoPause = autoPause,
                            paceAlert = paceAlert,
                            slowPaceSec = slow?.takeIf { paceAlert } ?: oldTr.slowPaceSec,
                            fastPaceSec = fast?.takeIf { paceAlert } ?: oldTr.fastPaceSec,
                            intervals = intervalsOn,
                            workSec = work?.takeIf { intervalsOn } ?: oldTr.workSec,
                            restSec = rest?.takeIf { intervalsOn } ?: oldTr.restSec,
                            repeats = rep?.takeIf { intervalsOn } ?: oldTr.repeats
                        )
                    )
                    app.settings.saveReports(ReportSettings(
                        minSaveKm = m!!, weekly = repWeekly, onImprovement = repImprove,
                        weeklyGoalKm = g!!, monthlyGoalKm = mg!!, autoGmail = autoGmail
                    ))
                    WalkRunWidget.refresh(context)
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

    restoreInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { restoreInfo = null },
            title = { Text("לשחזר מהגיבוי?") },
            text = {
                Text(
                    "הגיבוי מ-${info.modifiedTime.take(16).replace('T', ' ')} (UTC) יחליף את כל האימונים וההגדרות " +
                        "שבטלפון עכשיו. אימונים שנשמרו אחרי הגיבוי יימחקו. האפליקציה תיפתח מחדש."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreInfo = null
                    withDrive("משחזר...") { token -> DriveBackup.restore(app, token) }
                }) { Text("שחזור") }
            },
            dismissButton = { TextButton(onClick = { restoreInfo = null }) { Text("ביטול") } }
        )
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

private fun mmss(sec: Int) = TimeText.mmss(sec)

private fun parseMmss(text: String): Int? = TimeText.parse(text)

/** Time as m:ss, with ▲▼ arrows of [stepSec] seconds inside [minSec]..[maxSec]. */
@Composable
private fun TimeField(
    label: String,
    value: String,
    stepSec: Int = 10,
    minSec: Int = 10,
    maxSec: Int = 3600,
    onChange: (String) -> Unit
) {
    fun shift(sign: Int) {
        val current = parseMmss(value) ?: minSec
        val snapped = (current + stepSec / 2) / stepSec * stepSec // Snap to the step grid.
        onChange(mmss((snapped + sign * stepSec).coerceIn(minSec, maxSec)))
    }
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ':' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        trailingIcon = { StepArrows(onUp = { shift(1) }, onDown = { shift(-1) }) },
        modifier = Modifier.fillMaxWidth()
    )
}
private val BIRTH_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
