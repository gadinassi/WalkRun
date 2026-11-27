package com.example.walkrun.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.Period

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class Sex { MALE, FEMALE }

data class UserProfile(
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val birthDate: LocalDate = LocalDate.now().minusYears(DEFAULT_AGE),
    val sex: Sex = Sex.MALE
) {
    /** Age in full years today. Goes up by itself on the birthday. */
    val age: Int get() = Period.between(birthDate, LocalDate.now()).years

    companion object {
        const val DEFAULT_AGE = 40L
    }
}

/** Announcements by distance and by time are independent. Both can be on. */
data class AnnounceSettings(
    val enabled: Boolean = true,
    val byDistance: Boolean = true,
    val byTime: Boolean = false,
    val distanceKm: Double = 1.0,
    val timeMin: Int = 5,
    /** Turn music from other apps down while speaking. */
    val duckMusic: Boolean = true,
    /** After each km: its time and the change from the km before. */
    val kmSummary: Boolean = true
)

/**
 * [minSaveKm]: a workout shorter than this is not saved.
 * [weekly] / [onImprovement]: when to prepare an email report.
 */
data class ReportSettings(
    val minSaveKm: Double = 0.2,
    val weekly: Boolean = true,
    val onImprovement: Boolean = true,
    /** Weekly distance goal, Sunday to Saturday. 0 = no goal. */
    val weeklyGoalKm: Double = 0.0,
    /** Monthly distance goal, calendar month. 0 = no goal. */
    val monthlyGoalKm: Double = 0.0,
    /** Send the reports automatically with the Gmail API, instead of opening a draft. */
    val autoGmail: Boolean = false
)

/**
 * Settings for the workout itself.
 * Paces are seconds per km. Intervals: [workSec] effort, [restSec] rest, [repeats] rounds.
 */
data class TrainingSettings(
    val autoPause: Boolean = true,
    val paceAlert: Boolean = false,
    val slowPaceSec: Int = 600,
    val fastPaceSec: Int = 300,
    val intervals: Boolean = false,
    val workSec: Int = 120,
    val restSec: Int = 60,
    val repeats: Int = 8
)

enum class ThemeMode(val label: String) { SYSTEM("לפי המכשיר"), LIGHT("בהיר"), DARK("כהה") }

/** Display settings. They apply at once, without the Save button. */
data class DisplaySettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Keep the screen on while the workout screen is open. */
    val keepScreenOn: Boolean = true
)

data class Account(val name: String, val email: String) {
    companion object {
        /** Local user without a Google account. */
        val GUEST = Account(name = "אורח", email = "local")
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val HEIGHT = intPreferencesKey("height_cm")
        val WEIGHT = doublePreferencesKey("weight_kg")
        /** Old setting: age in years. Read only, to estimate the birth date after an update. */
        val AGE = intPreferencesKey("age")
        val BIRTH = longPreferencesKey("birth_epoch_day")
        val SEX = stringPreferencesKey("sex")
        val ANN_ENABLED = booleanPreferencesKey("announce_enabled")
        /** Old setting: "DISTANCE" or "TIME". Read only, to keep the user's choice after an update. */
        val ANN_MODE = stringPreferencesKey("announce_mode")
        val ANN_BY_DISTANCE = booleanPreferencesKey("announce_by_distance")
        val ANN_BY_TIME = booleanPreferencesKey("announce_by_time")
        val ANN_DISTANCE = doublePreferencesKey("announce_distance_km")
        val ANN_TIME = intPreferencesKey("announce_time_min")
        val ANN_DUCK = booleanPreferencesKey("announce_duck_music")
        val ANN_KM_SUMMARY = booleanPreferencesKey("announce_km_summary")
        val MIN_SAVE_KM = doublePreferencesKey("min_save_km")
        val REPORT_WEEKLY = booleanPreferencesKey("report_weekly")
        val REPORT_IMPROVE = booleanPreferencesKey("report_on_improvement")
        val WEEKLY_GOAL_KM = doublePreferencesKey("weekly_goal_km")
        val MONTHLY_GOAL_KM = doublePreferencesKey("monthly_goal_km")
        val AUTO_GMAIL = booleanPreferencesKey("report_auto_gmail")
        val THEME = stringPreferencesKey("theme_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val HEALTH_CONNECT = booleanPreferencesKey("health_connect")
        val DRIVE_BACKUP = booleanPreferencesKey("drive_backup")
        val CURRENT_SHOE = longPreferencesKey("current_shoe_id")
        val PLAN_ACTIVE = booleanPreferencesKey("plan_active")
        val PLAN_DONE = stringSetPreferencesKey("plan_done")
        val LAST_BACKUP = longPreferencesKey("last_backup_ms")
        val PACE_ALERT = booleanPreferencesKey("pace_alert")
        val SLOW_PACE = intPreferencesKey("pace_alert_slow_sec")
        val FAST_PACE = intPreferencesKey("pace_alert_fast_sec")
        val INTERVALS = booleanPreferencesKey("intervals")
        val WORK_SEC = intPreferencesKey("interval_work_sec")
        val REST_SEC = intPreferencesKey("interval_rest_sec")
        val REPEATS = intPreferencesKey("interval_repeats")
        val NAME = stringPreferencesKey("account_name")
        val EMAIL = stringPreferencesKey("account_email")
    }

    private val d = UserProfile()
    private val ad = AnnounceSettings()

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        UserProfile(
            heightCm = p[Keys.HEIGHT] ?: d.heightCm,
            weightKg = p[Keys.WEIGHT] ?: d.weightKg,
            birthDate = p[Keys.BIRTH]?.let { LocalDate.ofEpochDay(it) }
                ?: p[Keys.AGE]?.let { LocalDate.now().minusYears(it.toLong()) }
                ?: d.birthDate,
            sex = Sex.entries.firstOrNull { it.name == p[Keys.SEX] } ?: d.sex
        )
    }

    /** True after the user saved the personal data at least one time. */
    val profileConfigured: Flow<Boolean> = context.dataStore.data.map { it[Keys.HEIGHT] != null }

    val announce: Flow<AnnounceSettings> = context.dataStore.data.map { p ->
        val oldMode = p[Keys.ANN_MODE]
        AnnounceSettings(
            enabled = p[Keys.ANN_ENABLED] ?: ad.enabled,
            byDistance = p[Keys.ANN_BY_DISTANCE] ?: oldMode?.let { it == "DISTANCE" } ?: ad.byDistance,
            byTime = p[Keys.ANN_BY_TIME] ?: oldMode?.let { it == "TIME" } ?: ad.byTime,
            distanceKm = p[Keys.ANN_DISTANCE] ?: ad.distanceKm,
            timeMin = p[Keys.ANN_TIME] ?: ad.timeMin,
            duckMusic = p[Keys.ANN_DUCK] ?: ad.duckMusic,
            kmSummary = p[Keys.ANN_KM_SUMMARY] ?: ad.kmSummary
        )
    }

    private val rd = ReportSettings()

    val reports: Flow<ReportSettings> = context.dataStore.data.map { p ->
        ReportSettings(
            minSaveKm = p[Keys.MIN_SAVE_KM] ?: rd.minSaveKm,
            weekly = p[Keys.REPORT_WEEKLY] ?: rd.weekly,
            onImprovement = p[Keys.REPORT_IMPROVE] ?: rd.onImprovement,
            weeklyGoalKm = p[Keys.WEEKLY_GOAL_KM] ?: rd.weeklyGoalKm,
            monthlyGoalKm = p[Keys.MONTHLY_GOAL_KM] ?: rd.monthlyGoalKm,
            autoGmail = p[Keys.AUTO_GMAIL] ?: rd.autoGmail
        )
    }

    suspend fun saveReports(settings: ReportSettings) {
        context.dataStore.edit {
            it[Keys.MIN_SAVE_KM] = settings.minSaveKm
            it[Keys.REPORT_WEEKLY] = settings.weekly
            it[Keys.REPORT_IMPROVE] = settings.onImprovement
            it[Keys.WEEKLY_GOAL_KM] = settings.weeklyGoalKm
            it[Keys.MONTHLY_GOAL_KM] = settings.monthlyGoalKm
            it[Keys.AUTO_GMAIL] = settings.autoGmail
        }
    }

    private val td = TrainingSettings()

    val training: Flow<TrainingSettings> = context.dataStore.data.map { p ->
        TrainingSettings(
            autoPause = p[Keys.AUTO_PAUSE] ?: td.autoPause,
            paceAlert = p[Keys.PACE_ALERT] ?: td.paceAlert,
            slowPaceSec = p[Keys.SLOW_PACE] ?: td.slowPaceSec,
            fastPaceSec = p[Keys.FAST_PACE] ?: td.fastPaceSec,
            intervals = p[Keys.INTERVALS] ?: td.intervals,
            workSec = p[Keys.WORK_SEC] ?: td.workSec,
            restSec = p[Keys.REST_SEC] ?: td.restSec,
            repeats = p[Keys.REPEATS] ?: td.repeats
        )
    }

    suspend fun saveTraining(s: TrainingSettings) {
        context.dataStore.edit {
            it[Keys.AUTO_PAUSE] = s.autoPause
            it[Keys.PACE_ALERT] = s.paceAlert
            it[Keys.SLOW_PACE] = s.slowPaceSec
            it[Keys.FAST_PACE] = s.fastPaceSec
            it[Keys.INTERVALS] = s.intervals
            it[Keys.WORK_SEC] = s.workSec
            it[Keys.REST_SEC] = s.restSec
            it[Keys.REPEATS] = s.repeats
        }
    }

    val display: Flow<DisplaySettings> =context.dataStore.data.map { p ->
        DisplaySettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: ThemeMode.SYSTEM,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true
        )
    }

    suspend fun saveDisplay(settings: DisplaySettings) {
        context.dataStore.edit {
            it[Keys.THEME] = settings.theme.name
            it[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
        }
    }

    /** Nightly backup to Google Drive, and the time of the last successful backup (0 = never). */
    val driveBackup: Flow<Boolean> = context.dataStore.data.map { it[Keys.DRIVE_BACKUP] ?: false }
    val lastBackup: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_BACKUP] ?: 0L }

    suspend fun setDriveBackup(on: Boolean) {
        context.dataStore.edit { it[Keys.DRIVE_BACKUP] = on }
    }

    suspend fun setLastBackup(ms: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP] = ms }
    }

    /** The training plan: on or off, and the sessions done (PlanSession keys like "2-3"). */
    val planActive: Flow<Boolean> = context.dataStore.data.map { it[Keys.PLAN_ACTIVE] ?: false }
    val planDone: Flow<Set<String>> = context.dataStore.data.map { it[Keys.PLAN_DONE] ?: emptySet() }

    suspend fun setPlanActive(on: Boolean) {
        context.dataStore.edit { it[Keys.PLAN_ACTIVE] = on }
    }

    suspend fun markPlanDone(key: String) {
        context.dataStore.edit { it[Keys.PLAN_DONE] = (it[Keys.PLAN_DONE] ?: emptySet()) + key }
    }

    suspend fun resetPlan() {
        context.dataStore.edit { it.remove(Keys.PLAN_DONE) }
    }

    /** The shoes for new workouts. 0 = none. */
    val currentShoe: Flow<Long> = context.dataStore.data.map { it[Keys.CURRENT_SHOE] ?: 0L }

    suspend fun setCurrentShoe(id: Long) {
        context.dataStore.edit { it[Keys.CURRENT_SHOE] = id }
    }

    /** Write each saved workout to Health Connect. */
    val healthConnect: Flow<Boolean> = context.dataStore.data.map { it[Keys.HEALTH_CONNECT] ?: false }

    suspend fun setHealthConnect(on: Boolean) {
        context.dataStore.edit { it[Keys.HEALTH_CONNECT] = on }
    }

    suspend fun setAutoGmail(on: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_GMAIL] = on }
    }

    val account: Flow<Account?> = context.dataStore.data.map { p ->
        p[Keys.EMAIL]?.let { Account(p[Keys.NAME] ?: it, it) }
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit {
            it[Keys.HEIGHT] = profile.heightCm
            it[Keys.WEIGHT] = profile.weightKg
            it[Keys.BIRTH] = profile.birthDate.toEpochDay()
            it.remove(Keys.AGE)
            it[Keys.SEX] = profile.sex.name
        }
    }

    suspend fun saveAnnounce(settings: AnnounceSettings) {
        context.dataStore.edit {
            it[Keys.ANN_ENABLED] = settings.enabled
            it[Keys.ANN_BY_DISTANCE] = settings.byDistance
            it[Keys.ANN_BY_TIME] = settings.byTime
            it.remove(Keys.ANN_MODE)
            it[Keys.ANN_DISTANCE] = settings.distanceKm
            it[Keys.ANN_TIME] = settings.timeMin
            it[Keys.ANN_DUCK] = settings.duckMusic
            it[Keys.ANN_KM_SUMMARY] = settings.kmSummary
        }
    }

    suspend fun saveAccount(account: Account?) {
        context.dataStore.edit {
            if (account == null) {
                it.remove(Keys.NAME)
                it.remove(Keys.EMAIL)
            } else {
                it[Keys.NAME] = account.name
                it[Keys.EMAIL] = account.email
            }
        }
    }
}
