package com.axelsarassamit.gx12

import android.content.Context
import android.os.SystemClock
import app.pillion.core.*
import app.pillion.protocol.YamahaPhoneStatus
import app.pillion.protocol.YamahaStatusUpdates

data class PhoneStatusWrite(val battery: Int? = null, val charging: Boolean? = null, val signal: Int? = null,
    val headset: Boolean? = null, val count: Long = 0, val at: Long = 0)

data class BikeDataState(val active: Boolean = false, val authenticated: Boolean = false,
    val status: String = "Connect to your parked Yamaha to read bike data.", val packets: Long = 0,
    val lastPacket: Long = 0, val connectedAt:Long=0, val aliveRequests:Long=0, val aliveReplies:Long=0,
    val phoneWrites:Map<YamahaStatusUpdates.Kind,PhoneStatusWrite> = emptyMap(), val values: Map<BikeMetric, BikeValue> = emptyMap(),
    val faults: List<CcuTelemetry.Fault>? = null,
    val faultsAt: Long = 0, val ignition: Boolean? = null)

/** Live state and decoded ride-book metrics. Connection failures go to BikeDiagnostics. */
object BikeDataStore {
    private var logContext: Context? = null
    @Volatile var state = BikeDataState(); private set
    @Synchronized fun start(c: Context) {
        logContext=c.applicationContext
        state = BikeDataState(active = true, status = "Connecting to the paired Yamaha…")
        BikeDiagnostics.record(c, "CCU session started")
    }
    @Synchronized fun status(text: String, authenticated: Boolean) {
        state = state.copy(status = text, authenticated = authenticated, connectedAt=if(authenticated && state.connectedAt==0L) SystemClock.elapsedRealtime() else state.connectedAt)
        logContext?.let { BikeDiagnostics.record(it, "CCU status authenticated=$authenticated ${text.take(500)}") }
    }
    @Synchronized fun alive(reply:Boolean) {
        if(!state.active)return
        state=if(reply)state.copy(aliveReplies=state.aliveReplies+1) else state.copy(aliveRequests=state.aliveRequests+1)
    }
    @Synchronized fun stop(c: Context, text: String) {
        if (state.active) BikeDiagnostics.record(c, "CCU session stopped ${text.take(500)}")
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
            var updated = state.copy(values = values.toMap())
            when (message.command) {
                0x106 -> updated = updated.copy(faults = CcuTelemetry.faults(message), faultsAt = now)
                0x138 -> if (message.payload.size == 1 && message.payload[0].toInt() in 0..1)
                    updated = updated.copy(ignition = message.payload[0] == 1.toByte())
            }
            val telemetry = message.command in setOf(0x10f, 0x106, 0x138)
            state = if (telemetry) updated.copy(packets = state.packets + 1, lastPacket = now) else updated
            if (decoded.isNotEmpty()) RideBookStore.recordBike(c, decoded, "Vehicle", emptyList(), wall, now)
        } catch (error: Exception) { BikeDiagnostics.record(c, "CCU telemetry rejected command=${message.command} exception=${error.javaClass.simpleName}") }
    }
    @Synchronized fun phoneStatusWritten(c: Context, phone: YamahaPhoneStatus, kind: YamahaStatusUpdates.Kind) {
        if (!state.active || !state.authenticated) return
        val old = state.phoneWrites[kind]
        val write = PhoneStatusWrite(
            battery = if (kind in setOf(YamahaStatusUpdates.Kind.BATTERY, YamahaStatusUpdates.Kind.COMBINED)) phone.batteryPercent else null,
            charging = if (kind in setOf(YamahaStatusUpdates.Kind.BATTERY, YamahaStatusUpdates.Kind.COMBINED)) phone.charging else null,
            signal = if (kind in setOf(YamahaStatusUpdates.Kind.SIGNAL, YamahaStatusUpdates.Kind.COMBINED)) phone.signal?.wireValue else null,
            headset = if (kind in setOf(YamahaStatusUpdates.Kind.HEADSET, YamahaStatusUpdates.Kind.COMBINED)) phone.headset else null,
            count = (old?.count ?: 0) + 1, at = SystemClock.elapsedRealtime())
        state = state.copy(phoneWrites = state.phoneWrites + (kind to write))
        if (old == null) BikeDiagnostics.record(c, "CCU phone status write accepted kind=${kind.name}")
    }
}
