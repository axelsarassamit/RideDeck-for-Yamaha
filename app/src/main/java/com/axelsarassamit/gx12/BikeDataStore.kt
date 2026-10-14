package com.axelsarassamit.gx12

import android.content.Context
import android.os.SystemClock
import app.pillion.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BikeDataState(val active: Boolean = false, val authenticated: Boolean = false,
    val status: String = "Connect to your parked Yamaha to read bike data.", val packets: Long = 0,
    val lastPacket: Long = 0, val values: Map<BikeMetric, BikeValue> = emptyMap(),
    val raw: Map<String, Pair<String, Long>> = emptyMap(), val faults: List<CcuTelemetry.Fault>? = null,
    val faultsAt: Long = 0, val injection: Long? = null, val injectionAt: Long = 0, val ignition: Boolean? = null)

/** Private, bounded log and immutable UI snapshots. No authentication, VIN or pairing payload is admitted. */
object BikeDataStore {
    @Volatile var state = BikeDataState(); private set
    private val logNames = listOf("ccu-parked-test.previous.jsonl", "ccu-parked-test.jsonl")
    @Synchronized fun start(c: Context) {
        state = BikeDataState(active = true, status = "Connecting to the paired Yamaha…")
        append(c, JSONObject().put("event", "session_start").put("version", BuildConfig.VERSION_NAME).put("decoder", "xmax-1248-v1"))
    }
    @Synchronized fun status(text: String, authenticated: Boolean) { state = state.copy(status = text, authenticated = authenticated) }
    @Synchronized fun stop(c: Context, text: String) {
        if (state.active) append(c, JSONObject().put("event", "session_stop"))
        state = state.copy(active = false, authenticated = false, status = text)
    }
    @Synchronized fun accept(c: Context, message: CcuProtocol.Message) {
        if (!state.active || !state.authenticated) return
        val now = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis()
        try {
            if (message.command in setOf(0x10f, 0x10b, 0x10d, 0x127) && CcuProtocol.server(message) != 1248) return
            val items = CcuProtocol.readings(message)
            val decoded = CcuTelemetry.decode(message, now, wall)
            val values = state.values.toMutableMap()
            if (message.command == 0x10f && items.any { it.id == 1 }) {
                for (metric in BikeMetric.entries.filter { it != BikeMetric.ODOMETER }) values.remove(metric)
            }
            if (message.command == 0x10f && items.any { it.id == 72 }) values.remove(BikeMetric.ODOMETER)
            decoded.forEach { values[it.metric] = it }
            val raw = state.raw.toMutableMap()
            val permitted = when (message.command) {
                0x10f -> setOf(1, 3, 9, 72)
                0x114 -> setOf(521, 570, 682, 683, 698, 558, 574, 1028, 689)
                0x10b -> setOf(61857, 61858, 63233)
                else -> emptySet()
            }
            val safeItems = items.filter { it.id in permitted }
            val name = when (message.command) { 0x114 -> "CAN"; 0x10b -> "Counter"; else -> "Vehicle" }
            safeItems.forEach { raw["$name ID ${it.id}"] = hex(it.bytes).take(512) to wall }
            while (raw.size > 64) raw.remove(raw.keys.first())
            var updated = state.copy(values = values.toMap(), raw = raw.toMap())
            when (message.command) {
                0x106 -> updated = updated.copy(faults = CcuTelemetry.faults(message), faultsAt = now)
                0x120 -> updated = updated.copy(injection = CcuTelemetry.injectionCount(message), injectionAt = now)
                0x138 -> if (message.payload.size == 1 && message.payload[0].toInt() in 0..1)
                    updated = updated.copy(ignition = message.payload[0] == 1.toByte())
            }
            val telemetry = message.command in setOf(0x10f, 0x114, 0x106, 0x120, 0x138)
            state = if (telemetry) updated.copy(packets = state.packets + 1, lastPacket = now) else updated
            val event = JSONObject().put("command", message.command).put("fields", JSONArray().apply {
                safeItems.forEach { put(JSONObject().put("id", it.id).put("raw", hex(it.bytes))) }
            })
            if (message.command == 0x106) event.put("faults", JSONArray().apply { state.faults?.forEach {
                put(JSONObject().put("server", it.server).put("code", it.code).put("status", it.status))
            } })
            if (message.command == 0x120) event.put("injection_raw", state.injection)
            if (message.command == 0x138) event.put("ignition", state.ignition)
            // Market records are represented only by the fact a reply arrived, never arbitrary bytes.
            append(c, event)
            if (decoded.isNotEmpty() || safeItems.isNotEmpty()) RideBookStore.recordBike(c, decoded, name, safeItems, wall, now)
        } catch (error: Exception) { BikeDiagnostics.record(c, "CCU telemetry rejected command=${message.command} exception=${error.javaClass.simpleName}") }
    }
    @Synchronized fun mark(c: Context, value: String): Boolean {
        if (!state.active) return false
        val allowed = setOf("baseline", "front_on", "front_off", "rear_on", "rear_off", "left_on", "left_off", "right_on", "right_off", "hazards_on", "hazards_off")
        require(value in allowed)
        append(c, JSONObject().put("event", value).put("source", "user_observation"))
        RideBookStore.markBike(c, value, System.currentTimeMillis(), SystemClock.elapsedRealtime()); return true
    }
    @Synchronized fun export(c: Context): File {
        val out = File(c.cacheDir, "ccu-export.jsonl")
        out.outputStream().use { target -> for (name in logNames) {
            val file = File(c.filesDir, name); if (file.exists()) file.inputStream().use { it.copyTo(target) }
        } }
        return out
    }
    private fun hex(value: ByteArray) = value.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun append(c: Context, event: JSONObject) {
        runCatching {
            val file = File(c.filesDir, logNames.last())
            if (file.length() > 2_000_000) {
                val old = File(c.filesDir, logNames.first()); old.delete(); check(file.renameTo(old))
            }
            file.appendText(event.put("received_ms", System.currentTimeMillis()).put("elapsed_ms", SystemClock.elapsedRealtime()).toString() + "\n", Charsets.UTF_8)
        }.onFailure { BikeDiagnostics.record(c, "CCU private log unavailable ${it.javaClass.simpleName}") }
    }
}
