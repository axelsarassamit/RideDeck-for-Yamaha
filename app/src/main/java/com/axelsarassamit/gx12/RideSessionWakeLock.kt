package com.axelsarassamit.gx12

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** CPU protection for an active foreground session; never turns a locked screen back on. */
class RideSessionWakeLock(context: Context, name: String) {
    private val handler=Handler(Looper.getMainLooper())
    private val wake=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"${context.packageName}:$name").apply { setReferenceCounted(false) }
    private var active=false
    private var closed=false
    private val renew=object: Runnable {
        override fun run() { synchronized(this@RideSessionWakeLock) { if(active) hold() } }
    }
    private fun hold() {
        wake.acquire(10*60*1000L)
        handler.removeCallbacks(renew); handler.postDelayed(renew,5*60*1000L)
    }
    @Synchronized fun start() { if(!closed && !active) { active=true; hold() } }
    @Synchronized fun stop() {
        active=false; handler.removeCallbacks(renew)
        if(wake.isHeld) wake.release()
    }
    @Synchronized fun close() { closed=true; stop() }
}
