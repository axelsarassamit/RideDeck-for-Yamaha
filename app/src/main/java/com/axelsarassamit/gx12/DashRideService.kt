package com.axelsarassamit.gx12

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

/** Explicit ride timer and companion lease. Never restored by GPS, an Activity or saved preferences. */
class DashRideService : Service() {
    companion object {
        private val session=DashRideSessionState()
        @JvmField @Volatile var bridgeStatus="Start a ride to enable Dash in the background"
        @JvmStatic fun active()=session.active()
        @JvmStatic fun elapsed()=session.elapsed(SystemClock.elapsedRealtime())
        @JvmStatic fun start(c: Context) {
            try { c.startForegroundService(Intent(c,DashRideService::class.java).setAction("START")) }
            catch(_: RuntimeException) { bridgeStatus="Open RideDeck and try Start ride again" }
        }
        @JvmStatic fun pause(c: Context) { session.pause(SystemClock.elapsedRealtime());c.stopService(Intent(c,DashRideService::class.java)) }
        @JvmStatic fun end(c: Context) { session.end();c.stopService(Intent(c,DashRideService::class.java)) }
    }
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var live=false
    private var bridgeBusy=false
    private var wake: RideSessionWakeLock?=null
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="PAUSE") { session.pause(SystemClock.elapsedRealtime());stopSelf();return START_NOT_STICKY }
        if(intent?.action=="END") { session.end();stopSelf();return START_NOT_STICKY }
        if(intent?.action!="START") { stopSelf();return START_NOT_STICKY }
        if(live) return START_NOT_STICKY
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("dash_ride","Ride session",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,26,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String,code: Int)=PendingIntent.getService(this,code,Intent(this,DashRideService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"dash_ride").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("RideDeck ride active").setContentText("Ride timer active. Use Pause or End to stop.")
            .setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null,"Pause ride",action("PAUSE",27)).build())
            .addAction(Notification.Action.Builder(null,"End ride",action("END",28)).build()).build()
        try {
            if(Build.VERSION.SDK_INT>=34) startForeground(26,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(26,notification)
            wake=RideSessionWakeLock(this,"dash-ride").also { it.start() }
        } catch(error: RuntimeException) {
            bridgeStatus="Ride could not start. Open RideDeck and try again"
            BikeDiagnostics.record(this,"Dash ride start failed exception=${error.javaClass.simpleName}")
            stopSelf();return START_NOT_STICKY
        }
        session.start(SystemClock.elapsedRealtime());live=true
        bridgeStatus="Connecting to RideDeck Dash"
        BikeDiagnostics.record(this,"Dash ride started explicit=true")
        main.post(heartbeat)
        return START_NOT_STICKY
    }
    private val heartbeat=object: Runnable {
        override fun run() {
            if(!live || !session.active()) return
            if(!bridgeBusy) {
                bridgeBusy=true
                DashCompanion.renew(this@DashRideService,{ live && session.active() }) { result -> main.post {
                    bridgeBusy=false
                    if(live && session.active() && result!=null && result!=bridgeStatus) {
                        bridgeStatus=result;BikeDiagnostics.record(this@DashRideService,"Dash lease status=$result")
                    }
                } }
            }
            main.postDelayed(this,5000)
        }
    }
    override fun onTaskRemoved(rootIntent: Intent?) { BikeDiagnostics.record(this,"Explicit Dash ride continues after Recent apps removal");super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        live=false;main.removeCallbacksAndMessages(null)
        session.pause(SystemClock.elapsedRealtime())
        DashCompanion.stop(this)
        wake?.close();wake=null
        bridgeStatus=if(elapsed()>0) "Ride paused. Dash background session stopped" else "Ride ended. Dash background session stopped"
        BikeDiagnostics.record(this,"Dash ride service stopped active=false")
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
}
