package com.axelsarassamit.gx12

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.*
import android.telephony.*
import app.pillion.protocol.YamahaPhoneStatus
import app.pillion.protocol.YamahaStatusUpdates.Kind
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real phone readings only; owns no bike connection and sends no Bluetooth writes. */
@SuppressLint("MissingPermission")
class YamahaPhoneStatusMonitor(private val context: Context, private val bikeAddress: String,
                              private val changed: (YamahaPhoneStatus, Set<Kind>) -> Unit) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val reader = Executors.newSingleThreadScheduledExecutor()
    private val profiles = ConcurrentHashMap<Int, BluetoothProfile>()
    private val unsupportedProfiles = ConcurrentHashMap.newKeySet<Int>()
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    @Volatile private var running = false
    @Volatile private var generation = 0
    @Volatile private var network = YamahaPhoneStatus.Network.UNKNOWN
    @Volatile private var signalLevel: Int? = null
    @Volatile private var telephony: TelephonyManager? = null
    @Volatile private var subscription = Int.MIN_VALUE
    private var stopTelephony: (() -> Unit)? = null
    @Volatile var latest = YamahaPhoneStatus(); private set

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!running) { runCatching { adapter?.closeProfileProxy(profile, proxy) }; return }
            profiles[profile] = proxy; refresh()
        }
        override fun onServiceDisconnected(profile: Int) { profiles.remove(profile); refresh() }
    }

    fun start() {
        check(!running); running = true
        bindTelephony(defaultSubscription())
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            runCatching { adapter?.getProfileProxy(context, profileListener, profile) == true }
                .onSuccess { if (!it) unsupportedProfiles.add(profile) }
        }
        reader.scheduleWithFixedDelay({ read(emptySet()) }, 0, 5, TimeUnit.SECONDS)
    }

    fun refresh(force: Set<Kind> = emptySet()) {
        if (running) runCatching { reader.execute { read(force) } }
    }

    private fun defaultSubscription() = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(-1)

    private fun bindTelephony(id: Int) {
        generation++; val current = generation; subscription = id
        runCatching { stopTelephony?.invoke() }; stopTelephony = null
        network = YamahaPhoneStatus.Network.UNKNOWN; signalLevel = null
        val base = context.getSystemService(TelephonyManager::class.java) ?: return
        val manager = if (id >= 0) base.createForSubscriptionId(id) else base
        telephony = manager
        fun signal(value: SignalStrength) { if (running && current == generation) { signalLevel = value.level; refresh() } }
        fun service(value: ServiceState) {
            if (running && current == generation) {
                network = when (value.state) {
                    ServiceState.STATE_IN_SERVICE -> YamahaPhoneStatus.Network.IN_SERVICE
                    ServiceState.STATE_OUT_OF_SERVICE -> YamahaPhoneStatus.Network.OUT_OF_SERVICE
                    ServiceState.STATE_EMERGENCY_ONLY -> YamahaPhoneStatus.Network.EMERGENCY_ONLY
                    ServiceState.STATE_POWER_OFF -> YamahaPhoneStatus.Network.POWER_OFF
                    else -> YamahaPhoneStatus.Network.UNKNOWN
                }
                refresh()
            }
        }
        runCatching {
            stopTelephony = if (Build.VERSION.SDK_INT >= 31) Api31.listen(context, manager, ::signal, ::service)
                else legacyListen(manager, ::signal, ::service)
        }.onFailure { BikeDiagnostics.record(context, "CCU phone signal unavailable exception=${it.javaClass.simpleName}") }
    }

    private fun read(force: Set<Kind>) {
        if (!running) return
        val id = defaultSubscription()
        if (id != subscription) {
            main.post { if (running) { if (subscription != id) bindTelephony(id); refresh(force) } }; return
        }
        val current = generation
        val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val absent = runCatching { telephony?.simState == TelephonyManager.SIM_STATE_ABSENT }.getOrDefault(false)
        val snapshot = YamahaPhoneStatus(YamahaPhoneStatus.percent(level, scale), charging,
            YamahaPhoneStatus.cellSignal(absent, network, signalLevel), headset(),
            volume(AudioManager.STREAM_VOICE_CALL), volume(AudioManager.STREAM_MUSIC), thermal())
        main.post { if (running && generation == current) { latest = snapshot; changed(snapshot, force) } }
    }

    private fun volume(stream: Int): Int? = runCatching {
        val audio = context.getSystemService(AudioManager::class.java) ?: return@runCatching null
        val maximum = audio.getStreamMaxVolume(stream)
        val current = audio.getStreamVolume(stream)
        if (maximum <= 0 || current !in 0..maximum) null else current * 100 / maximum
    }.getOrNull()

    private fun thermal(): Int? = if (Build.VERSION.SDK_INT >= 29) runCatching {
        when (context.getSystemService(PowerManager::class.java)?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE -> 0
            PowerManager.THERMAL_STATUS_SEVERE -> 1
            PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> 2
            else -> null
        }
    }.getOrNull() else null

    private fun headset(): Boolean? {
        val enabled = runCatching { adapter?.isEnabled }.getOrNull() ?: return null
        if (!enabled) return false
        val classic = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).map { id ->
            if (id in unsupportedProfiles) emptyList() else runCatching {
                profiles[id]?.connectedDevices?.filterNot {
                    val name = it.name.orEmpty()
                    name.startsWith("YCCU_", true) || name.startsWith("YSCCU_", true)
                }?.map { it.address }
            }.getOrNull()
        }
        val ble = runCatching {
            val audio = context.getSystemService(AudioManager::class.java) ?: return@runCatching null
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                    (Build.VERSION.SDK_INT >= 28 && it.type == AudioDeviceInfo.TYPE_HEARING_AID)
            }
        }.getOrNull()
        return YamahaPhoneStatus.headset(bikeAddress, classic, ble)
    }

    override fun close() {
        running = false; generation++
        reader.shutdownNow()
        runCatching { stopTelephony?.invoke() }; stopTelephony = null
        profiles.forEach { (id, proxy) -> runCatching { adapter?.closeProfileProxy(id, proxy) } }
        profiles.clear(); unsupportedProfiles.clear()
    }

    @Suppress("DEPRECATION") private fun legacyListen(manager: TelephonyManager, signal: (SignalStrength) -> Unit,
                                                      service: (ServiceState) -> Unit): () -> Unit {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(value: SignalStrength) = signal(value)
            override fun onServiceStateChanged(value: ServiceState) = service(value)
        }
        manager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_SERVICE_STATE)
        return { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    @androidx.annotation.RequiresApi(31)
    private object Api31 {
        fun listen(context: Context, manager: TelephonyManager, signal: (SignalStrength) -> Unit,
                   service: (ServiceState) -> Unit): () -> Unit {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.ServiceStateListener {
                override fun onSignalStrengthsChanged(value: SignalStrength) = signal(value)
                override fun onServiceStateChanged(value: ServiceState) = service(value)
            }
            if (Build.VERSION.SDK_INT >= 33) manager.registerTelephonyCallback(TelephonyManager.INCLUDE_LOCATION_DATA_NONE, context.mainExecutor, callback)
            else manager.registerTelephonyCallback(context.mainExecutor, callback)
            return { manager.unregisterTelephonyCallback(callback) }
        }
    }
}
