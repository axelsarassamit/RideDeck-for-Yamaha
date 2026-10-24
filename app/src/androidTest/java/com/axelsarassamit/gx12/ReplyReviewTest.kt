package com.axelsarassamit.gx12

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Synthetic recipient, no reply action and no Send/Open/Speak action is invoked. */
class ReplyReviewTest {
    @Test fun editedDraftAndLargeControlsSurviveRecreationAndRotation() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".preview"))
        val p=RidePreferences.prefs(c);val old=p.all["map_auto"];p.edit().putBoolean("map_auto",false).commit()
        fun views()=WindowInspector.getGlobalWindowViews().flatMap{SharedSetupTest.views(it)}
        fun draft()=views().filterIsInstance<EditText>().single{it.tag=="reply_review_draft"}
        try {
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                s.onActivity { a ->
                    val recipient=GX12NotificationListener.NotificationPreview("Synthetic test rider","Fixture only","fixture",null,"com.whatsapp","Test chat")
                    MainActivity::class.java.getDeclaredMethod("confirmVoiceReply",GX12NotificationListener.NotificationPreview::class.java,String::class.java)
                        .apply{isAccessible=true}.invoke(a,recipient,"Initial fixture draft")
                    draft().setText("Edited draft retained without sending")
                }
                s.recreate();i.waitForIdleSync()
                s.onActivity{assertEquals("Edited draft retained without sending",draft().text.toString())}
                for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                    s.onActivity{it.requestedOrientation=orientation}
                    repeat(50){i.waitForIdleSync();var ready=false;s.onActivity{a->ready=a.resources.configuration.orientation==if(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE};if(!ready)Thread.sleep(100)}
                    s.onActivity { a ->
                        assertEquals("Edited draft retained without sending",draft().text.toString())
                        for(label in listOf("Send reply","Speak again","Cancel")) {
                            val b=views().filterIsInstance<Button>().single{it.text.toString()==label}
                            val r=Rect();assertTrue("Hidden reply control: $label",b.getGlobalVisibleRect(r))
                            assertTrue("Reply control too short",r.height()>=a.resources.displayMetrics.density*87)
                            if(label=="Send reply")assertFalse("No synthetic message can be sent",b.isEnabled)
                        }
                    }
                }
                s.onActivity{views().filterIsInstance<Button>().single{it.text.toString()=="Cancel"}.performClick()}
            }
        } finally {p.edit().apply{if(old is Boolean)putBoolean("map_auto",old)else remove("map_auto")}.commit()}
    }
}
