package com.axelsarassamit.gx12

import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** English audio samples only. Never inserts GPS, starts a route or sends a message. */
class GuidanceVoiceTest {
    @Test fun englishAdvanceApproachAndTurnNowCompleteOnInstalledApp() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val main=Handler(Looper.getMainLooper())
        val initialized=CountDownLatch(1)
        val finished=CountDownLatch(3)
        val error=AtomicReference<String?>()
        var engine:TextToSpeech?=null
        ActivityScenario.launch(SetupActivity::class.java).use {
            try {
                instrumentation.runOnMainSync {
                    engine=TextToSpeech(instrumentation.targetContext) { result -> main.post {
                        val voice=engine
                        if(result!=TextToSpeech.SUCCESS || voice==null || !NavigationVoice.configure(voice))error.set("English voice unavailable")
                        else {
                            voice.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            voice.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                                override fun onStart(id:String?){}
                                override fun onDone(id:String?){finished.countDown()}
                                @Deprecated("Legacy callback") override fun onError(id:String?){error.set("Speech failed: $id");while(finished.count>0)finished.countDown()}
                            })
                        }
                        initialized.countDown()
                    } }
                }
                assertTrue("Speech initialization timed out",initialized.await(15,TimeUnit.SECONDS))
                assertNull(error.get())
                val scheduler=NavigationAnnouncements().apply{reset(false)}
                instrumentation.runOnMainSync {
                    for((index,meters) in listOf(590.0,230.0,50.0).withIndex()){
                        val cue=scheduler.next(10,-2,meters,"Oak Road",20.0,false,index*10000L)!!
                        assertEquals(TextToSpeech.SUCCESS,engine!!.speak(cue.text,TextToSpeech.QUEUE_ADD,null,"guidance-sample-$index"))
                        scheduler.accepted(cue,index*10000L)
                    }
                }
                assertTrue("Speech samples did not finish",finished.await(45,TimeUnit.SECONDS))
                assertNull(error.get())
            } finally { instrumentation.runOnMainSync{engine?.shutdown()} }
        }
    }
}
