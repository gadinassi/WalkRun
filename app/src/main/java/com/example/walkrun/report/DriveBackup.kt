package com.example.walkrun.report

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.walkrun.MainActivity
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.system.exitProcess

/**
 * Backup of the workout database and the settings to the hidden app folder of the user's Google Drive
 * ("drive.appdata" scope: only WalkRun can see these files, it cannot see any other Drive file).
 */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val TAG = "DriveBackup"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val DB_NAME = "walkrun.db"
    private const val SETTINGS_NAME = "settings.preferences_pb"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

    data class Info(val modifiedTime: String, val sizeBytes: Long)

    suspend fun email(app: WalkRunApp): String? =
        app.settings.account.first()?.email?.takeIf { it != Account.GUEST.email && it.contains('@') }

    suspend fun token(app: WalkRunApp): String? {
        val e = email(app) ?: return null
        return (GoogleAccess.authorize(app, e, SCOPE) as? GoogleAccess.Auth.Token)?.value
    }

    private fun dbFile(context: Context) = context.getDatabasePath(DB_NAME)
    private fun settingsFile(context: Context) = File(context.filesDir, "datastore/$SETTINGS_NAME")

    /** Uploads the database and the settings. Replaces the previous backup. */
    suspend fun backup(app: WalkRunApp, token: String) = withContext(Dispatchers.IO) {
        // Write the WAL into the main file, so the one file holds all the data.
        app.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        upload(token, DB_NAME, dbFile(app).readBytes(), "application/x-sqlite3")
        settingsFile(app).takeIf { it.exists() }?.let { upload(token, SETTINGS_NAME, it.readBytes(), "application/octet-stream") }
        app.settings.setLastBackup(System.currentTimeMillis())
    }

    /** Info about the backup in Drive, or null when there is none. */
    suspend fun info(token: String): Info? = withContext(Dispatchers.IO) {
        val f = find(token, DB_NAME) ?: return@withContext null
        Info(f.optString("modifiedTime"), f.optString("size").toLongOrNull() ?: 0)
    }

    /**
     * Replaces the local data with the backup, then restarts the app.
     * The downloaded file is checked first: a bad download never replaces the data.
     */
    suspend fun restore(app: WalkRunApp, token: String) {
        val (db, settings) = withContext(Dispatchers.IO) {
            val dbId = find(token, DB_NAME)?.getString("id") ?: error("No backup")
            val db = download(token, dbId)
            require(db.size > SQLITE_HEADER.size && db.copyOfRange(0, SQLITE_HEADER.size).contentEquals(SQLITE_HEADER)) {
                "The backup is not a database file"
            }
            db to find(token, SETTINGS_NAME)?.getString("id")?.let { download(token, it) }
        }
        app.database.close()
        val target = dbFile(app)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        target.writeBytes(db)
        settings?.let { settingsFile(app).writeBytes(it) }
        // Room and DataStore keep the old data in memory: start the app again.
        app.startActivity(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        exitProcess(0)
    }

    /** Nightly job: only when the switch is on and the approval is still valid. */
    suspend fun nightly(app: WalkRunApp) {
        if (!app.settings.driveBackup.first()) return
        val last = app.settings.lastBackup.first()
        val newest = app.workouts.allOnce().maxOfOrNull { it.endTime } ?: return
        if (newest <= last) return // Nothing new since the last backup.
        try {
            val t = token(app) ?: return
            backup(app, t)
        } catch (e: Exception) {
            Log.w(TAG, "Nightly backup failed", e)
        }
    }

    // ---------------- Drive REST ----------------

    private fun find(token: String, name: String): JSONObject? {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        val conn = open("$API?spaces=appDataFolder&q=$q&fields=files(id,name,modifiedTime,size)", token)
        try {
            GoogleAccess.check(conn)
            val files = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("files")
            return if (files.length() > 0) files.getJSONObject(0) else null
        } finally {
            conn.disconnect()
        }
    }

    private fun upload(token: String, name: String, bytes: ByteArray, mime: String) {
        val existing = find(token, name)?.getString("id")
        val conn = if (existing != null) {
            // Replace the content. HttpURLConnection has no PATCH; Google accepts the override header.
            open("$UPLOAD/$existing?uploadType=media", token).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", mime)
                doOutput = true
                outputStream.use { it.write(bytes) }
            }
        } else {
            val boundary = "walkrun-" + UUID.randomUUID()
            val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
            open("$UPLOAD?uploadType=multipart", token).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                outputStream.use { out ->
                    out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
                    out.write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                    out.write(bytes)
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }
            }
        }
        try {
            GoogleAccess.check(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun download(token: String, id: String): ByteArray {
        val conn = open("$API/$id?alt=media", token)
        try {
            GoogleAccess.check(conn)
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, token: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        setRequestProperty("Authorization", "Bearer $token")
    }
}
