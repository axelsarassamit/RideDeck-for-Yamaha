package com.axelsarassamit.gx12

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/** Yamaha backend adapter for the same native Dash controls used by standard RideDeck. */
object DashRideUi {
    @JvmStatic fun show(activity: Activity) { Session(activity).show(false) }
    @JvmStatic fun toggle(activity: Activity) {
        if(DashRideService.active()) DashRideService.pause(activity) else Session(activity).show(true)
    }

    private class Session(private val activity: Activity) : DashRideControls.Host, Application.ActivityLifecycleCallbacks {
        private val main = Handler(Looper.getMainLooper())
        private val preferences = activity.getSharedPreferences("dash_companion",Activity.MODE_PRIVATE)
        private val autoRequest = DashAutoOpenRequest()
        private val controls = DashRideControls(activity,this)
        private var resumed = true
        private var previewGeneration = if(DashRecordingConnection.connected() && DashRideService.active()) DashRideService.generation() else -1L
        private var rear: Bitmap? = null
        private var front: Bitmap? = null
        private var rearAt = 0L
        private var frontAt = 0L

        fun show(startFromGesture: Boolean) {
            activity.application.registerActivityLifecycleCallbacks(this)
            controls.show()
            main.post(tick)
            if(startFromGesture) main.postDelayed({ if(visible()) onRideToggle() },100)
        }
        private fun visible() = resumed && controls.isFocused && !activity.isFinishing && !activity.isDestroyed
        private fun enabled() = preferences.getBoolean("auto_open",true)
        override fun state(): DashRideControls.State {
            val active = DashRideService.active()
            val camera = DashRecordingConnection.STATUS.state(SystemClock.elapsedRealtime())
            return DashRideControls.State().apply {
                dashAvailable = DashCompanion.installed(activity)
                rideActive = active
                elapsedMillis = DashRideService.elapsed()
                rideHasElapsed = elapsedMillis > 0
                recordingRequested = active && previewGeneration == DashRideService.generation()
                recording = camera == DashRecordingStatus.State.RECORDING
                recordingStatusFresh = DashRecordingConnection.STATUS.fresh(SystemClock.elapsedRealtime())
                starting = camera == DashRecordingStatus.State.STARTING
                accepted = DashRecordingConnection.connected()
                autoRecord = enabled()
                detail = listOf(DashRecordingConnection.STATUS.label(SystemClock.elapsedRealtime()),
                    DashRideService.bridgeStatus,DashRideService.recordingStatus).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
        override fun onRideToggle() {
            autoRequest.cancel();main.removeCallbacks(autoAttempt)
            if(DashRideService.active()) {
                DashRideService.pause(activity);previewGeneration = -1;clearFrames()
            } else {
                if(enabled() && DashCompanion.installed(activity) && !DashRecordingConnection.permissionsGranted(activity)) {
                    DashRideService.recordingStatus = "Allow camera and microphone, then tap Start or Resume again."
                    val missing = listOf(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO).filter {
                        activity.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    Toast.makeText(activity,"Allow Yamaha to connect Dash's camera and microphone without changing screens.",Toast.LENGTH_LONG).show()
                    activity.requestPermissions(missing.toTypedArray(),92)
                    controls.refresh();return
                }
                if(visible() && enabled() && DashCompanion.installed(activity)) {
                    autoRequest.begin(DashRideService.generation()+1,SystemClock.elapsedRealtime())
                    main.postDelayed(autoAttempt,100)
                }
                DashRideService.start(activity)
            }
            controls.refresh()
        }
        override fun onRideEnd() {
            autoRequest.cancel();main.removeCallbacks(autoAttempt);DashRideService.end(activity)
            previewGeneration = -1;clearFrames();controls.refresh()
        }
        override fun onRecordWithDashChanged(enabled: Boolean) {
            preferences.edit().putBoolean("auto_open",enabled).apply()
            if(!enabled) { autoRequest.cancel();DashRecordingConnection.cancelPending() }
        }
        override fun onDashSettings() { DashCompanion.open(activity) }
        override fun onDismiss() {
            autoRequest.cancel();main.removeCallbacksAndMessages(null);DashRecordingConnection.cancelPending()
            DashRecordingConnection.stopPreview();clearFrames()
            activity.application.unregisterActivityLifecycleCallbacks(this)
        }
        private fun clearFrames() { rear = null;front = null;rearAt = 0;frontAt = 0;controls.clearFrames() }
        private fun decode(bytes: ByteArray?): Bitmap? {
            if(!DashRecordingReply.frameAllowed(bytes)) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes!!,0,bytes.size,bounds)
            if(bounds.outWidth !in 1..1280 || bounds.outHeight !in 1..1280) return null
            return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        }
        private val tick = object: Runnable {
            override fun run() {
                if(!controls.isShowing) return
                if(visible() && DashRideService.active() && DashCompanion.installed(activity)) {
                    DashRecordingConnection.refreshStatus()
                    if(controls.previewVisible() && previewGeneration == DashRideService.generation()) {
                        val now = SystemClock.elapsedRealtime()
                        if(now-rearAt >= 1000) rear = null
                        if(now-frontAt >= 1000) front = null
                        controls.setPreviewFrames(rear,front)
                        DashRecordingConnection.preview(previewGeneration) { data ->
                            if(!visible() || previewGeneration != DashRideService.generation() || !DashRideService.active()) return@preview
                            rear = decode(data?.getByteArray("rearJpeg"));front = decode(data?.getByteArray("frontJpeg"))
                            val received = SystemClock.elapsedRealtime()
                            rearAt = if(rear != null) received else 0;frontAt = if(front != null) received else 0
                            controls.setPreviewFrames(rear,front);controls.refresh()
                            if(data?.getBoolean("recording") != true) controls.setPreviewStatus(DashRecordingReply.displayMessage(data?.getString("message")))
                        }
                    } else { DashRecordingConnection.stopPreview();clearFrames() }
                } else { DashRecordingConnection.stopPreview();clearFrames() }
                main.postDelayed(this,500)
            }
        }
        private val autoAttempt = object: Runnable {
            override fun run() {
                if(!visible() || !enabled() || !DashCompanion.installed(activity)) { autoRequest.cancel();return }
                val now = SystemClock.elapsedRealtime()
                if(!autoRequest.waiting(now)) return
                if(!autoRequest.ready(DashRideService.generation(),DashRideService.active(),now)) { main.postDelayed(this,100);return }
                val requestId = autoRequest.id();val generation = DashRideService.generation()
                DashCompanion.renew(activity,{ DashRideService.active() && DashRideService.generation() == generation }) { result -> main.post {
                    if(autoRequest.complete(requestId,DashRideService.generation(),DashRideService.active(),SystemClock.elapsedRealtime(),
                        result == "Dash background enabled",visible(),enabled(),DashCompanion.installed(activity))) {
                        DashRecordingConnection.start(activity,generation) { visible() && enabled() }
                        previewGeneration = generation;clearFrames();controls.refresh()
                    }
                } }
            }
        }
        override fun onActivityPaused(a: Activity) { if(a === activity) {
            resumed = false;autoRequest.cancel();main.removeCallbacks(autoAttempt);main.removeCallbacks(tick)
            DashRecordingConnection.cancelPending();DashRecordingConnection.stopPreview();DashRecordingConnection.stopStatusPolling()
            clearFrames();controls.pause()
        } }
        override fun onActivityResumed(a: Activity) { if(a === activity && controls.isShowing) {
            resumed = true;controls.refresh();main.removeCallbacks(tick);main.post(tick)
        } }
        override fun onActivityCreated(a: Activity,saved: Bundle?) { }
        override fun onActivityStarted(a: Activity) { }
        override fun onActivityStopped(a: Activity) { }
        override fun onActivitySaveInstanceState(a: Activity,out: Bundle) { }
        override fun onActivityDestroyed(a: Activity) { }
    }
}
