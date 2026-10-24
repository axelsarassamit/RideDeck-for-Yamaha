package com.axelsarassamit.gx12

import android.content.Context
import android.content.Intent
import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/** Explicit app close, distinct from Home, screen off and configuration changes. */
object RideAppExit {
    /** A queued foreground start must be acknowledged before stopping, including on Xiaomi. */
    fun rejectQueuedStart(service:Service,type:Int):Int {
        try {
            service.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("app_exit","App shutdown",NotificationManager.IMPORTANCE_MIN))
            val notice=Notification.Builder(service,"app_exit").setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setContentTitle("RideDeck stopped").build()
            if(Build.VERSION.SDK_INT>=29 && (type!=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE || Build.VERSION.SDK_INT>=34))
                service.startForeground(79,notice,type) else service.startForeground(79,notice)
        } catch(_:RuntimeException) { }
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        service.stopSelf()
        return Service.START_NOT_STICKY
    }
    @JvmStatic fun closed(c:Context)=c.getSharedPreferences("app_lifecycle",0).getBoolean("closed",false)
    @JvmStatic fun opened(c:Context) {
        if(closed(c)) {
            c.getSharedPreferences("app_lifecycle",0).edit().putBoolean("closed",false).commit()
            YamahaCastService.autoReconnectPaused=false
            GX12NotificationListener.resumeAfterExit(c)
        }
    }
    @JvmStatic fun stop(c:Context) {
        c.getSharedPreferences("app_lifecycle",0).edit().putBoolean("closed",true).commit()
        YamahaCastService.autoReconnectPaused=true
        YamahaCastService.automaticFallbackPending=false
        YamahaBikeAutoConnect.pause()
        RideBackgroundSession.navigationStopped(c)
        RideBackgroundSession.castStopped(c)
        BikeDataService.stop(c)
        c.stopService(Intent(c,YamahaCastService::class.java))
        c.stopService(Intent(c,NativeNavigationService::class.java))
        NativeNavigation.detach()
        DashRideService.pause(c)
        GX12NotificationListener.pauseForExit()
        BikeDiagnostics.record(c,"App closed: navigation, bike data, reconnect and ride session stopped")
    }
}
