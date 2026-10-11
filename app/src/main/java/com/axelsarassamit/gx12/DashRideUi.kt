package com.axelsarassamit.gx12

import android.app.Activity
import android.app.Dialog
import android.app.Application
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.*
import java.util.Locale

object DashRideUi {
    @JvmStatic fun show(activity: Activity) {
        val main=Handler(Looper.getMainLooper())
        val preferences=activity.getSharedPreferences("dash_companion",Activity.MODE_PRIVATE)
        val autoRequest=DashAutoOpenRequest()
        fun dp(n: Int)=(n*activity.resources.displayMetrics.density).toInt()
        val dialog=Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(0xff101722.toInt()) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(16),bars.top+dp(16),bars.right+dp(16),bars.bottom+dp(16));insets
        }
        fun text(size: Float)=TextView(activity).apply { textSize=size;setTextColor(Color.WHITE);setPadding(0,0,0,dp(12)) }
        val title=text(28f);page.addView(title)
        val state=text(24f);page.addView(state)
        val detail=text(18f);page.addView(detail)
        val previewArea=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE }
        val angle=text(20f).apply { text="Check both camera angles before riding." };previewArea.addView(angle)
        val previewState=text(18f);previewArea.addView(previewState)
        val cameras=LinearLayout(activity)
        fun camera(label: String): ImageView {
            val column=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            column.addView(text(18f).apply { text=label })
            val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.BLACK);contentDescription="$label live camera angle" }
            column.addView(image,LinearLayout.LayoutParams(-1,dp(180)))
            cameras.addView(column,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),0) })
            return image
        }
        val rear=camera("Rear camera");val front=camera("Front camera");previewArea.addView(cameras)
        page.addView(previewArea)
        var previewGeneration=-1L;var rearAt=0L;var frontAt=0L
        fun clearPreview() { rear.setImageDrawable(null);front.setImageDrawable(null);rearAt=0;frontAt=0 }
        fun frame(view: ImageView,bytes: ByteArray?): Boolean {
            if(!DashRecordingReply.frameAllowed(bytes)) { view.setImageDrawable(null);return false }
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes!!,0,bytes.size,bounds)
            if(bounds.outWidth !in 1..1280 || bounds.outHeight !in 1..1280) { view.setImageDrawable(null);return false }
            val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            view.setImageBitmap(bitmap);return bitmap!=null
        }
        val explanation=text(18f).apply { text="Start or resume a ride to record with Dash and check the camera angles here. Pause or End stops its background session." };page.addView(explanation)
        val autoStart=Switch(activity).apply {
            text="Record with Dash";textSize=22f;setTextColor(Color.WHITE);minHeight=dp(80)
            isChecked=preferences.getBoolean("auto_open",true)
            setOnCheckedChangeListener { _,enabled ->
                preferences.edit().putBoolean("auto_open",enabled).apply()
                if(!enabled)autoRequest.cancel()
            }
        };page.addView(autoStart,LinearLayout.LayoutParams(-1,dp(80)))
        fun button(label: String,primary: Boolean=false)=Button(activity).apply {
            text=label;isAllCaps=false;textSize=22f;minHeight=dp(80);setTextColor(Color.WHITE)
            background=GradientDrawable().apply { setColor(if(primary)0xff087eff.toInt() else 0xff242f40.toInt());cornerRadius=dp(12).toFloat() }
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        val toggle=button("Start ride",true)
        val end=button("End ride")
        val row=LinearLayout(activity)
        row.addView(toggle,LinearLayout.LayoutParams(0,dp(80),1f).apply { rightMargin=dp(8) })
        row.addView(end,LinearLayout.LayoutParams(0,dp(80),1f))
        page.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        val open=button("Dash settings").apply { setOnClickListener { DashCompanion.open(activity) } }
        page.addView(open,LinearLayout.LayoutParams(-1,dp(80)).apply { topMargin=dp(12) })
        val done=button("Close").apply { setOnClickListener { dialog.dismiss() } }
        // Keep Done reachable above the camera images in either orientation.
        page.addView(done,1,LinearLayout.LayoutParams(-1,dp(80)).apply { bottomMargin=dp(12) })
        fun visible()=dialog.isShowing && !activity.isFinishing && !activity.isDestroyed && dialog.window?.decorView?.hasWindowFocus()==true
        val previewTick=object: Runnable {
            override fun run() {
                if(!visible() || previewGeneration!=DashRideService.generation() || !DashRideService.active() || !DashCompanion.installed(activity)) {
                    DashRecordingConnection.stopPreview();clearPreview();previewArea.visibility=android.view.View.GONE;return
                }
                val now=android.os.SystemClock.elapsedRealtime()
                if(now-rearAt>=1000)rear.setImageDrawable(null)
                if(now-frontAt>=1000)front.setImageDrawable(null)
                if(now-rearAt>=1000 && now-frontAt>=1000)previewState.text=DashRideService.recordingStatus.ifBlank { "Waiting for live images. Keep the bike parked." }
                DashRecordingConnection.preview(previewGeneration) { data ->
                    if(!visible() || previewGeneration!=DashRideService.generation() || !DashRideService.active())return@preview
                    val received=android.os.SystemClock.elapsedRealtime()
                    rearAt=if(data!=null && frame(rear,data.getByteArray("rearJpeg")))received else { rear.setImageDrawable(null);0 }
                    frontAt=if(data!=null && frame(front,data.getByteArray("frontJpeg")))received else { front.setImageDrawable(null);0 }
                    previewState.text=if(data?.getBoolean("recording")==true) {
                        if(rearAt>0 || frontAt>0)"Recording · check the camera angles" else "Recording · waiting for live images"
                    } else DashRecordingReply.displayMessage(data?.getString("message"))
                }
                main.postDelayed(this,500)
            }
        }
        val autoAttempt=object: Runnable {
            override fun run() {
                if(!visible() || !autoStart.isChecked || !DashCompanion.installed(activity)) { autoRequest.cancel();return }
                val now=android.os.SystemClock.elapsedRealtime()
                if(!autoRequest.waiting(now))return
                if(!autoRequest.ready(DashRideService.generation(),DashRideService.active(),now)) { main.postDelayed(this,100);return }
                val requestId=autoRequest.id();val generation=DashRideService.generation()
                // Authenticate the explicit rider gesture before binding recording without opening Dash.
                DashCompanion.renew(activity,{ DashRideService.active() && DashRideService.generation()==generation }) { result -> main.post {
                    if(autoRequest.complete(requestId,DashRideService.generation(),DashRideService.active(),android.os.SystemClock.elapsedRealtime(),
                        result=="Dash background enabled",visible(),autoStart.isChecked,DashCompanion.installed(activity))) {
                        DashRecordingConnection.start(activity,generation) { visible() && autoStart.isChecked }
                        previewGeneration=generation;previewArea.visibility=android.view.View.VISIBLE;done.text="Done"
                        previewState.text="Recording requested. Waiting for live images.";clearPreview()
                        main.removeCallbacks(previewTick);main.post(previewTick)
                    }
                } }
            }
        }
        val lifecycle=object: Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: Activity) { if(a===activity) {
                autoRequest.cancel();main.removeCallbacks(autoAttempt);DashRecordingConnection.cancelPending()
                main.removeCallbacks(previewTick);DashRecordingConnection.stopPreview();clearPreview()
            } }
            override fun onActivityCreated(a: Activity,saved: Bundle?) { }
            override fun onActivityStarted(a: Activity) { }
            override fun onActivityResumed(a: Activity) { if(a===activity && previewGeneration>=0)main.postDelayed(previewTick,200) }
            override fun onActivityStopped(a: Activity) { }
            override fun onActivitySaveInstanceState(a: Activity,out: Bundle) { }
            override fun onActivityDestroyed(a: Activity) { }
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        val update=object: Runnable {
            override fun run() {
                if(!dialog.isShowing) return
                val active=DashRideService.active();val elapsed=DashRideService.elapsed();val seconds=elapsed/1000
                val dashAvailable=DashCompanion.installed(activity)
                title.text=if(dashAvailable)"RideDeck Dash" else "Ride session"
                for(view in listOf(detail,explanation,autoStart,open))view.visibility=if(dashAvailable)android.view.View.VISIBLE else android.view.View.GONE
                val time=String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
                state.text=if(active)"Ride active · $time" else if(elapsed>0)"Ride paused · $time" else "Ready to ride"
                toggle.text=if(active)"Pause ride" else if(elapsed>0)"Resume ride" else "Start ride"
                end.isEnabled=active || elapsed>0
                val cameraState=DashRecordingConnection.STATUS.state(android.os.SystemClock.elapsedRealtime())
                title.text=if(dashAvailable && cameraState==DashRecordingStatus.State.RECORDING)"● REC · RideDeck Dash" else if(dashAvailable)"RideDeck Dash" else "Ride session"
                title.setTextColor(if(dashAvailable && cameraState==DashRecordingStatus.State.RECORDING)0xffff4c4c.toInt() else Color.WHITE)
                detail.text=if(dashAvailable)listOf(DashRecordingConnection.STATUS.label(android.os.SystemClock.elapsedRealtime()),DashRideService.bridgeStatus,DashRideService.recordingStatus).filter { it.isNotBlank() }.joinToString("\n") else ""
                if(!dashAvailable || !active || previewGeneration!=DashRideService.generation()) { previewArea.visibility=android.view.View.GONE;clearPreview() }
                if(previewArea.visibility==android.view.View.VISIBLE)for(view in listOf(detail,explanation,autoStart))view.visibility=android.view.View.GONE
                main.postDelayed(this,500)
            }
        }
        toggle.setOnClickListener {
            autoRequest.cancel();main.removeCallbacks(autoAttempt)
            if(DashRideService.active()) DashRideService.pause(activity) else {
                if(autoStart.isChecked && DashCompanion.installed(activity) && !DashRecordingConnection.permissionsGranted(activity)) {
                    DashRideService.recordingStatus="Allow camera and microphone, then tap Start or Resume again."
                    val missing=listOf(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO).filter {
                        activity.checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    Toast.makeText(activity,"Allow Yamaha to connect Dash's camera and microphone without changing screens.",Toast.LENGTH_LONG).show()
                    activity.requestPermissions(missing.toTypedArray(),92)
                    main.removeCallbacks(update);main.postDelayed(update,250)
                    return@setOnClickListener
                }
                if(visible() && autoStart.isChecked && DashCompanion.installed(activity)) {
                    autoRequest.begin(DashRideService.generation()+1,android.os.SystemClock.elapsedRealtime())
                    main.postDelayed(autoAttempt,100)
                }
                DashRideService.start(activity)
            }
            main.removeCallbacks(update);main.postDelayed(update,250)
        }
        end.setOnClickListener { autoRequest.cancel();main.removeCallbacks(autoAttempt);DashRideService.end(activity);main.removeCallbacks(update);main.postDelayed(update,250) }
        dialog.setContentView(ScrollView(activity).apply { isFillViewport=true;setBackgroundColor(0xff101722.toInt());addView(page) })
        dialog.setOnDismissListener {
            autoRequest.cancel();main.removeCallbacksAndMessages(null);DashRecordingConnection.cancelPending()
            DashRecordingConnection.stopPreview();clearPreview();activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        }
        title.text=if(DashCompanion.installed(activity))"RideDeck Dash" else "Ride session"
        val dashVisible=DashCompanion.installed(activity)
        for(view in listOf(detail,explanation,autoStart,open))view.visibility=if(dashVisible)android.view.View.VISIBLE else android.view.View.GONE
        dialog.show();dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xff101722.toInt()))
            setLayout(-1,-1);addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ScreenChrome.apply(this,true)
        };androidx.core.view.ViewCompat.requestApplyInsets(page);main.post(update)
    }
}
