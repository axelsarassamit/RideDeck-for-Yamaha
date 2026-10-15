package com.axelsarassamit.gx12

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

/** Explicitly started foreground reader. No pairing reset, actuator command or automatic start on boot. */
class BikeDataService : Service() {
    companion object {
        private var current: BikeDataService? = null
        fun start(c: Context, address: String, duringRide: Boolean) {
            try { c.startForegroundService(Intent(c, BikeDataService::class.java).setAction("START")
                .putExtra("device", address).putExtra("during_ride", duringRide)) }
            catch (_: RuntimeException) { BikeDataStore.status("Open RideDeck and try the bike-data connection again.", false) }
        }
        fun stop(c: Context) { c.stopService(Intent(c, BikeDataService::class.java)) }
        fun readSnapshot() { current?.client?.readSnapshot() ?: BikeDataStore.status("Connect to the parked Yamaha first.", false) }
    }
    private val main = Handler(Looper.getMainLooper())
    private var client: CcuDiagnosticClient? = null
    private var wake: RideSessionWakeLock? = null
    private var closing = false
    private val endTest = Runnable {
        BikeDataStore.stop(this, "Parked test finished after 10 minutes. Reconnect to start another session."); stopSelf()
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != "START") { stopSelf(); return START_NOT_STICKY }
        if (client != null) { BikeDataStore.status("Bike data is already connecting or connected. Stop it before choosing another bike.", client?.isAuthenticated == true); return START_NOT_STICKY }
        val device = runCatching {
            val manager = getSystemService(BluetoothManager::class.java)
            manager.adapter.bondedDevices.firstOrNull { it.address == intent.getStringExtra("device") &&
                (it.name.orEmpty().contains("YCCU", true) || it.name.orEmpty().contains("Yamaha", true)) }
        }.getOrNull()
        if (device == null) { BikeDataStore.status("Selected Yamaha pairing unavailable. Check Bluetooth and Nearby devices permission.", false); stopSelf(); return START_NOT_STICKY }
        // Another GATT client may still own the data session after its screen was closed.
        // Do not authenticate over that session or disconnect a connection belonging to it.
        val alreadyConnected=runCatching {
            getSystemService(BluetoothManager::class.java).getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
                .any { it.address.equals(device.address,true) }
        }.getOrDefault(false)
        if(alreadyConnected) {
            BikeDataStore.start(this)
            BikeDataStore.stop(this,"The Yamaha data service is already connected to another app. Disconnect Y-Connect and nRF Connect first; closing their screen may leave them connected. The map connection can remain in use.")
            stopSelf();return START_NOT_STICKY
        }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel("yamaha_data", "Yamaha bike data", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 45, Intent(this, BikeDataActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 46, Intent(this, BikeDataService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "yamaha_data").setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("RideDeck Yamaha bike data").setContentText("Bike readings stay connected in the background. Use Stop to disconnect.")
                .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop bike data", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(45, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(45, notification)
            wake = RideSessionWakeLock(this, "bike-data").also { it.start() }
        } catch (_: RuntimeException) {
            BikeDataStore.status("Bike data could not start. Open RideDeck and allow Nearby devices, then try again.", false)
            stopSelf(); return START_NOT_STICKY
        }
        current = this; BikeDataStore.start(this)
        client = CcuDiagnosticClient(applicationContext,
            { message -> BikeDataStore.status(message, client?.isAuthenticated == true) },
            { message -> BikeDataStore.accept(applicationContext, message) },
            { if (!closing) { BikeDataStore.stop(this, BikeDataStore.state.status); stopSelf() } })
        client!!.start(device)
        if (!intent.getBooleanExtra("during_ride", false)) main.postDelayed(endTest, 600000)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        closing = true; main.removeCallbacksAndMessages(null); client?.close(); client = null
        wake?.close(); wake = null; if (current === this) current = null
        if (BikeDataStore.state.active) BikeDataStore.stop(this, "Bike-data connection stopped. Last readings are retained as history.")
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
