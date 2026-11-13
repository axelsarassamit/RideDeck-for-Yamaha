package com.axelsarassamit.gx12

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.maplibre.android.offline.OfflineManager

/** Configure once before map resources load. Never clear intentional offline regions. */
object MapDiskCache {
    private val main=Handler(Looper.getMainLooper())
    private var ready=false
    private var configuring=false
    private val callbacks=mutableListOf<()->Unit>()
    fun configure(c:Context,done:()->Unit) {
        check(Looper.myLooper()==Looper.getMainLooper())
        // SDK request logs can contain provider-key URLs; our own diagnostics record safe stages.
        org.maplibre.android.log.Logger.setVerbosity(org.maplibre.android.log.Logger.NONE)
        if(ready){done();return}
        callbacks.add(done)
        if(configuring)return
        configuring=true
        var finished=false
        fun finish(success:Boolean) { main.post {
            if(finished)return@post
            finished=true
            ready=true;configuring=false
            BikeDiagnostics.record(c,"Map disk cache configured=$success")
            val pending=callbacks.toList();callbacks.clear();pending.forEach{it()}
        } }
        // Cache maintenance must never strand navigation at startup if the native callback stalls.
        main.postDelayed({finish(false)},2500)
        runCatching {
            OfflineManager.getInstance(c).setMaximumAmbientCacheSize(MapCachePolicy.diskBudget(c.filesDir.usableSpace),
                object:OfflineManager.FileSourceCallback {
                    override fun onSuccess(){finish(true)}
                    override fun onError(message:String){finish(false)}
                })
        }.onFailure{finish(false)}
    }
}
