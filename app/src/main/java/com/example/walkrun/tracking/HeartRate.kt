package com.example.walkrun.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** The latest heart rate from any source: a Bluetooth strap or the WalkRun watch app. */
object HeartRateHub {
    data class Reading(val bpm: Int, val atRealtime: Long, val source: String)

    private val _latest = MutableStateFlow<Reading?>(null)
    val latest: StateFlow<Reading?> = _latest.asStateFlow()

    /** A reading older than this is not used (the sensor was lost). */
    private const val MAX_AGE_MS = 10_000L

    fun update(bpm: Int, source: String) {
        if (bpm in 30..240) _latest.value = Reading(bpm, SystemClock.elapsedRealtime(), source)
    }

    /** The current heart rate, or null when there is no recent reading. */
    fun current(): Int? = _latest.value?.takeIf { SystemClock.elapsedRealtime() - it.atRealtime <= MAX_AGE_MS }?.bpm

    fun clear() {
        _latest.value = null
    }
}

/** Heart rate zones as a share of the maximum heart rate. */
object HrZones {
    val NAMES = listOf("התאוששות", "קל", "אירובי", "סף", "מקסימלי")
    private val LOWER = listOf(0.5, 0.6, 0.7, 0.8, 0.9)

    /** Common estimate: 220 minus the age. */
    fun maxHr(age: Int): Int = (220 - age).coerceIn(120, 220)

    /** 1..5, or 0 below 50% of the maximum. */
    fun zone(bpm: Int, maxHr: Int): Int {
        val r = bpm.toDouble() / maxHr
        return LOWER.indexOfLast { r >= it } + 1
    }

    /** Milliseconds in each zone 1..5 (index 0..4), from (elapsedMs, bpm) pairs in time order. */
    fun timeInZones(samples: List<Pair<Long, Int>>, maxHr: Int, maxGapMs: Long = 15_000): LongArray {
        val out = LongArray(5)
        for (i in 1 until samples.size) {
            val dt = samples[i].first - samples[i - 1].first
            if (dt <= 0 || dt > maxGapMs) continue // A pause or a lost sensor is not counted.
            val z = zone(samples[i - 1].second, maxHr)
            if (z > 0) out[z - 1] += dt
        }
        return out
    }
}

/**
 * Standard Bluetooth heart rate strap (Heart Rate Service 0x180D, measurement 0x2A37).
 * Works with Polar, Garmin, Wahoo and most chest straps. Reconnects by itself (autoConnect).
 */
class HeartRateMonitor(private val context: Context) {
    private var gatt: BluetoothGatt? = null

    companion object {
        val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "HeartRate"

        /** Heart rate value of a 0x2A37 measurement: 8 or 16 bits, by bit 0 of the flags. */
        fun parse(value: ByteArray): Int? {
            if (value.size < 2) return null
            val sixteen = value[0].toInt() and 0x01 != 0
            return if (sixteen) {
                if (value.size < 3) null else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            } else value[1].toInt() and 0xFF
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            Log.d(TAG, "Connection state $newState (status $status)")
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(SERVICE)?.getCharacteristic(MEASUREMENT) ?: return
            g.setCharacteristicNotification(ch, true)
            val d = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == MEASUREMENT) parse(value)?.let { HeartRateHub.update(it, "strap") }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && ch.uuid == MEASUREMENT) ch.value?.let { v -> parse(v)?.let { HeartRateHub.update(it, "strap") } }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        disconnect()
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            // No Bluetooth permission, Bluetooth off, or a bad address: the workout goes on without it.
            Log.w(TAG, "Cannot connect", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
    }
}
