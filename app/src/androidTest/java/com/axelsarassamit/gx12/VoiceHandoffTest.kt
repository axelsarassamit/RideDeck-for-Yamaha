package com.axelsarassamit.gx12

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.SystemClock

/** A short actual recording and microphone handoff. No fake GPS, message send or bike operation. */
class VoiceHandoffTest {
    @Test fun recordingReleasesMicrophoneForVoiceAndResumesSameRide() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val createdRide=!DashRideService.active()
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            if(createdRide)scenario.onActivity { DashRideService.start(it) }
            fun waitFor(label:String, timeout:Long=20000, check:()->Boolean) {
                val end=SystemClock.elapsedRealtime()+timeout
                while(!check() && SystemClock.elapsedRealtime()<end)Thread.sleep(150)
                assertTrue(label,check())
            }
            fun cameraRecording():Boolean {
                i.runOnMainSync { DashRecordingConnection.refreshStatus() }
                return DashRecordingConnection.STATUS.state(SystemClock.elapsedRealtime())==DashRecordingStatus.State.RECORDING
            }
            try {
                waitFor("Ride did not start"){DashRideService.active()}
                Thread.sleep(1000)
                scenario.onActivity { a->DashRecordingConnection.start(a,DashRideService.generation()){ !a.isFinishing && !a.isDestroyed } }
                waitFor("Dash did not confirm real camera recording",30000){cameraRecording()}
                Thread.sleep(2500)
                val ready=CountDownLatch(1)
                scenario.onActivity { a->
                    MainActivity::class.java.getDeclaredMethod("withHeadsetMicrophone",Runnable::class.java).apply { isAccessible=true }
                        .invoke(a,Runnable { ready.countDown() })
                }
                assertTrue("Voice microphone was never released",ready.await(25,TimeUnit.SECONDS))
                i.runOnMainSync { DashRecordingConnection.refreshStatus() }
                Thread.sleep(1000)
                assertEquals(DashRecordingStatus.State.STOPPED,DashRecordingConnection.STATUS.state(SystemClock.elapsedRealtime()))
                scenario.onActivity { a->
                    val mic=MainActivity::class.java.getDeclaredField("headsetMic").apply { isAccessible=true }.get(a) as HeadsetMicRoute
                    mic.release()
                    MainActivity::class.java.getDeclaredMethod("resumeDashAfterVoice").apply { isAccessible=true }.invoke(a)
                }
                waitFor("Dash did not resume the same ride",30000){cameraRecording()}
                Thread.sleep(2500)
            } finally {
                val done=CountDownLatch(1)
                DashCompanion.pauseForVoice(c,{true}){done.countDown()}
                done.await(15,TimeUnit.SECONDS)
                if(createdRide)i.runOnMainSync { DashRideService.end(c) }
            }
        }
    }
}
