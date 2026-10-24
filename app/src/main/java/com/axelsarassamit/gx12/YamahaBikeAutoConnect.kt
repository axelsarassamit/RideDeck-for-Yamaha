package com.axelsarassamit.gx12

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.pillion.core.CcuAutoConnect

/** Reuses the map's actual paired peer. No independent discovery, bonding or boot receiver. */
object YamahaBikeAutoConnect {
    private val main = Handler(Looper.getMainLooper())
    private val policy = CcuAutoConnect()
    private var scheduled: Runnable? = null
    fun enabled(c: Context) = RidePreferences.prefs(c).getBoolean("bike_data_auto", true)
    fun connected(c: Context, address: String) = main.post {
        if(RideAppExit.closed(c))return@post
        cancel(); policy.connected(address); schedule(c.applicationContext, address, 3000)
    }
    fun disconnected(c: Context, address: String) = main.post {
        if (policy.peer.equals(address, true)) {
            cancel(); policy.disconnected(); BikeDataService.stopAutomatic(c)
        }
    }
    fun pause() { cancel(); policy.pause() }
    fun changed(c: Context) {
        cancel()
        if (!enabled(c)) { policy.pause(); BikeDataService.stopAutomatic(c); return }
        val peer = YamahaCastService.connectedAddress
        if (peer.isNotBlank()) connected(c, peer)
    }
    fun dataEnded(c: Context) {
        if (!RideAppExit.closed(c) && policy.peer.isNotBlank() && !policy.paused) schedule(c.applicationContext, policy.peer, 30000)
    }
    private fun cancel() { scheduled?.let(main::removeCallbacks); scheduled = null }
    private fun schedule(c: Context, address: String, delay: Long) {
        cancel()
        val task = Runnable {
            scheduled = null
            val permitted = Build.VERSION.SDK_INT < 31 ||
                (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                 c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            if (RideAppExit.closed(c) || !permitted || !YamahaCastService.connectedAddress.equals(address, true)) return@Runnable
            if (!policy.attempt(address, enabled(c), BikeDataStore.state.active)) return@Runnable
            BikeDiagnostics.record(c, "CCU automatic data start attempt=${policy.attempts}")
            BikeDataService.start(c, address, duringRide = true, automatic = true)
        }
        scheduled = task; main.postDelayed(task, delay)
    }
}
