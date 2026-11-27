package com.example.walkrun.data

import android.util.Log
import com.example.walkrun.BuildConfig
import com.example.walkrun.tracking.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Walking route between the points the user tapped, along streets and paths.
 * Uses the public OSRM foot server of the OpenStreetMap Germany association (FOSSGIS).
 * Only the tapped points are sent. Returns null when the server cannot be reached.
 */
object RouteSnapper {
    private const val SERVER = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"

    suspend fun route(waypoints: List<LatLon>): List<LatLon>? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        val coords = waypoints.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) }
        val conn = (URL("$SERVER$coords?overview=full&geometries=geojson").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "WalkRun/${BuildConfig.VERSION_NAME} (Android; personal use)")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("code") != "Ok") return@withContext null
            val line = json.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            (0 until line.length()).map { i -> line.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) } }
        } catch (e: Exception) {
            Log.w("RouteSnapper", "Routing failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }
}
