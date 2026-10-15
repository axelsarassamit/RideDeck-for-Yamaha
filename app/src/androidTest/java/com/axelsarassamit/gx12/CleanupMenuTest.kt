package com.axelsarassamit.gx12

import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Exercise the visible menu after removing its hidden Standard-edition entries. */
@SdkSuppress(minSdkVersion=29)
class CleanupMenuTest {
    @Test fun visibleCustomizationAndReplyChoiceKeepTheirActions() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=RidePreferences.prefs(context)
        val hadBefore=prefs.contains("reply_mode")
        val before=prefs.getInt("reply_mode",0)
        fun controls()=WindowInspector.getGlobalWindowViews().flatMap{ReleaseMenuTest.views(it)}.filterIsInstance<TextView>().filter{it.isShown}
        fun click(label:String) {
            val view=controls().lastOrNull{it.text.toString()==label} ?: error("Missing control: $label")
            var action:View=view
            while(!action.isClickable && action.parent is View) {
                val parent=action.parent
                if(parent is android.widget.AdapterView<*>) {
                    val position=parent.getPositionForView(action)
                    assertTrue(parent.performItemClick(action,position,parent.getItemIdAtPosition(position)))
                    return
                }
                action=parent as View
            }
            assertTrue("Unresponsive control: $label",action.performClick())
        }
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("showPersonalization").apply{isAccessible=true}.invoke(activity)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val labels=controls().map{it.text.toString()}
                    val expected=listOf("Phone mount: Left / Centre / Right","Messaging apps (choose several)","Reply method","Phone panel placement","Color theme","Music player","Home and Work destinations","Favorites","Nearby fuel stations","Bike position icon")
                    expected.forEach{label->assertTrue("Missing Yamaha setting: $label",labels.contains(label))}
                    assertFalse(labels.contains("Navigation / rider app"))
                    assertFalse(labels.contains("Bike map size"))
                    click("Reply method")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(controls().any{it.text.toString()=="Voice to text - confirm before sending"})
                    click(if(before==0) "Voice message - open original app" else "Voice to text - confirm before sending")
                    assertEquals(if(before==0) 1 else 0,prefs.getInt("reply_mode",0))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { click("Done") }
            } finally { prefs.edit().apply { if(hadBefore) putInt("reply_mode",before) else remove("reply_mode") }.commit() }
        }
    }
}
