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
    @Test fun landscapeMusicKeepsLongMetadataAndAllPlaybackControlsVisible() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=RidePreferences.prefs(instrumentation.targetContext)
        val hadPanel=prefs.contains("phone_panel");val before=prefs.getString("phone_panel","map")
        prefs.edit().putString("phone_panel","music").commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var originalOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
                scenario.onActivity { activity -> originalOrientation=activity.requestedOrientation;activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                try {
                    var landscape=false
                    for(attempt in 0..50) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { landscape=it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                        if(landscape)break
                        Thread.sleep(100)
                    }
                    assertTrue("Activity did not enter landscape",landscape)
                    // Configuration changes arrive before the physical window finishes rotating.
                    var settled=0
                    for(attempt in 0..40) {
                        instrumentation.waitForIdleSync()
                        var laidOut=false
                        scenario.onActivity { activity ->
                            val root=activity.window.decorView
                            laidOut=root.width>root.height && !root.isLayoutRequested
                        }
                        settled=if(laidOut)settled+1 else 0
                        if(settled>=5)break
                        Thread.sleep(200)
                    }
                    assertTrue("Landscape window did not settle",settled>=5)
                    scenario.onActivity { activity ->
                        fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
                        (field("handler") as android.os.Handler).removeCallbacks(field("trackRefresh") as Runnable)
                        (field("trackStatus") as TextView).text="Landscape sample: a long song title to check available space"
                        (field("trackArtist") as TextView).text="Sample artist with a long name"
                        (field("phoneGuidance") as TextView).text="In 190 m, turn left onto Sukhumvit Road."
                    }
                    Thread.sleep(200)
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity) as View
                        val root=activity.window.decorView
                        val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                        root.draw(android.graphics.Canvas(bitmap))
                        val folder=java.io.File(activity.getExternalFilesDir("tests"),"cleanup41-previews").apply{mkdirs()}
                        java.io.File(folder,"landscape-music-sample.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                        bitmap.recycle()
                        for(name in listOf("trackStatus","trackArtist","previousButton","playPauseButton","nextButton")) {
                            val view=field(name);val visible=android.graphics.Rect()
                            assertTrue("Hidden music control: $name",view.getGlobalVisibleRect(visible))
                            assertTrue("Clipped music control: $name visible=$visible view=${view.width}x${view.height}",visible.height()>=view.height-1 && visible.width()>=view.width-1)
                            if(name.endsWith("Button"))assertTrue("Playback target too short",view.height>=55*activity.resources.displayMetrics.density)
                        }
                    }
                } finally { scenario.onActivity { it.requestedOrientation=originalOrientation } }
            }
        } finally { prefs.edit().apply{if(hadPanel)putString("phone_panel",before)else remove("phone_panel")}.commit() }
    }

}
