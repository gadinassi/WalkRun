package com.example.walkrun.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** GPX 1.1 file of a workout, for Strava, Garmin Connect and other apps. One track segment per pause. */
object GpxExport {
    private val ISO = DateTimeFormatter.ISO_INSTANT
    private val NAME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneId.systemDefault())

    fun write(context: Context, w: WorkoutEntity, points: List<TrackPointEntity>): File {
        val file = File(File(context.cacheDir, "reports").apply { mkdirs() },
            "WalkRun_${NAME_FMT.format(Instant.ofEpochMilli(w.startTime))}.gpx")
        file.writeText(build(w, points))
        return file
    }

    /** The GPX text. No Android calls, so it is covered by unit tests. */
    fun build(w: WorkoutEntity, points: List<TrackPointEntity>): String {
        val type = WorkoutType.from(w.type)
        val name = w.title ?: "${type.label} ${NAME_FMT.format(Instant.ofEpochMilli(w.startTime))}"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"WalkRun\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>").append(esc(name)).append("</name><time>")
            .append(ISO.format(Instant.ofEpochMilli(w.startTime))).append("</time></metadata>\n")
        sb.append("  <trk>\n    <name>").append(esc(name)).append("</name>\n")
        // Strava reads the type from the track type: "running" or "walking".
        sb.append("    <type>").append(if (type == WorkoutType.RUN) "running" else "walking").append("</type>\n")
        for (seg in points.groupBy { it.segment }.toSortedMap().values) {
            sb.append("    <trkseg>\n")
            for (p in seg) {
                sb.append(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon))
                p.altitude?.let { sb.append(String.format(Locale.US, "<ele>%.1f</ele>", it)) }
                sb.append("<time>").append(ISO.format(Instant.ofEpochMilli(p.time))).append("</time></trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n</gpx>\n")
        return sb.toString()
    }

    /** Opens the share sheet with the GPX file. */
    fun share(context: Context, w: WorkoutEntity, points: List<TrackPointEntity>) {
        val file = write(context, w, points)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "ייצוא GPX"))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
