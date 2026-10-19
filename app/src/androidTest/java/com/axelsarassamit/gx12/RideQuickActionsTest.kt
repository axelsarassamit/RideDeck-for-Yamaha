package com.axelsarassamit.gx12

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Preview only. No actual route start, bike connection or production preference changes. */
class RideQuickActionsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun buttons(a:Activity)=SharedSetupTest.views(a.window.decorView).filterIsInstance<Button>()
    private fun bounds(v:View)=Rect().also{assertTrue("Visible ${v.contentDescription}",v.getGlobalVisibleRect(it));assertTrue(it.width()>0 && it.height()>0)}
    private fun <T:Activity> rotate(s:ActivityScenario<T>,orientation:Int) {
        s.onActivity{it.requestedOrientation=orientation}
        repeat(60){instrumentation.waitForIdleSync();var ready=false;s.onActivity{a->ready=a.resources.configuration.orientation==if(orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT};if(ready)return;Thread.sleep(100)}
        fail("Orientation did not settle")
    }
    private inline fun isolated(block:()->Unit) {
        val c=instrumentation.targetContext;check(c.packageName.endsWith(".preview"))
        val p=RidePreferences.prefs(c);val keys=listOf("map_auto","map_startup","phone_panel","bike_home");val backup=keys.associateWith{p.all[it]}
        p.edit().putBoolean("map_auto",false).putBoolean("map_startup",false).commit();RideBackgroundSession.navigationStopped(c)
        try{block()}finally{p.edit().apply{for((key,value) in backup)when(value){is Boolean->putBoolean(key,value);is String->putString(key,value);else->remove(key)}}.commit();RideBackgroundSession.navigationStopped(c)}
    }
    @Test fun mainActionsRemainVisibleForMapAndMusicInBothOrientations()=isolated {
        val p=RidePreferences.prefs(instrumentation.targetContext)
        for(panel in listOf("map","music")) {
            p.edit().putString("phone_panel",panel).commit()
            ActivityScenario.launch(MainActivity::class.java).use{s->
                for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                    rotate(s,orientation);instrumentation.waitForIdleSync()
                    s.onActivity{a->
                        val actions=buttons(a).filter{it.contentDescription?.startsWith("Ride action:")==true}
                        assertEquals(listOf("Search","Fun ride","Ride tools"),actions.map{it.text.toString()})
                        val rects=actions.map(::bounds);for(i in rects.indices)for(j in 0 until i)assertFalse(Rect.intersects(rects[i],rects[j]))
                        val all=SharedSetupTest.views(a.window.decorView)
                        val media=bounds(all.single{it.tag=="ride_media_pane"});val messages=bounds(all.single{it.tag=="ride_message_pane"})
                        assertFalse(Rect.intersects(media,messages));for(r in rects){assertFalse(Rect.intersects(r,media));assertFalse(Rect.intersects(r,messages))}
                        assertTrue("Media remains useful",media.height()>=a.resources.displayMetrics.density*64)
                        assertTrue("Messages remain useful",messages.height()>=a.resources.displayMetrics.density*64)
                        assertTrue(buttons(a).any{it.contentDescription=="Camera"});assertTrue(buttons(a).any{it.contentDescription=="Voice"})
                        SharedSetupTest.shot(a,"direct-$panel-${a.resources.configuration.orientation}")
                        if(panel=="music") {
                            all.single{it.tag=="ride_navigation_instruction"}.performClick()
                            assertTrue(SharedSetupTest.has(a,"Navigation instruction"));assertFalse(SharedSetupTest.has(a,"Search destination"))
                            SharedSetupTest.click(a,"Close")
                        }
                    }
                }
            }
        }
    }
    @Test fun funRideOpensDirectlyAndToolsSavedPlacesAreDirectScreens()=isolated {
        ActivityScenario.launch(MainActivity::class.java).use{s->
            val monitor=instrumentation.addMonitor(FunRideActivity::class.java.name,null,false)
            try{s.onActivity{a->buttons(a).single{it.contentDescription=="Ride action: Fun ride"}.performClick()};val opened=instrumentation.waitForMonitorWithTimeout(monitor,5000);assertTrue(opened is FunRideActivity);instrumentation.runOnMainSync{opened.finish()}}
            finally{instrumentation.removeMonitor(monitor)}
        }
        ActivityScenario.launch(RideToolsActivity::class.java).use{s->
            for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                rotate(s,orientation);instrumentation.waitForIdleSync();s.onActivity{a->
                    val tiles=buttons(a).filter{it.contentDescription?.startsWith("Ride tools:")==true && it.text.toString()!="Back"}
                    assertEquals(9,tiles.size);tiles.forEach{val r=bounds(it);assertEquals("Whole tile visible: ${it.text}",it.height,r.height())};SharedSetupTest.shot(a,"direct-tools-${a.resources.configuration.orientation}")
                }
            }
            val monitor=instrumentation.addMonitor(FavoritePlacesActivity::class.java.name,null,false)
            try{s.onActivity{a->buttons(a).single{it.text.toString()=="Saved places"}.performClick()};val opened=instrumentation.waitForMonitorWithTimeout(monitor,5000);assertTrue(opened is FavoritePlacesActivity);instrumentation.runOnMainSync{opened.finish()}}
            finally{instrumentation.removeMonitor(monitor)}
        }
    }
    @Test fun savedHomeOpensRouteReviewWithoutStartingNavigation()=isolated {
        val c=instrumentation.targetContext
        assertEquals("Grant precise location to the isolated preview before this check",android.content.pm.PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION))
        RidePreferences.prefs(c).edit().putString("bike_home","13.0,100.0").commit()
        val old=NativeNavigation.route
        ActivityScenario.launch(RideToolsActivity::class.java).use{s->
            val monitor=instrumentation.addMonitor(RoutePreviewActivity::class.java.name,android.app.Instrumentation.ActivityResult(0,null),true)
            try {
                s.onActivity{a->buttons(a).single{it.text.toString()=="Home"}.performClick()}
                instrumentation.waitForIdleSync();assertEquals(1,monitor.hits);assertSame(old,NativeNavigation.route)
            } finally {instrumentation.removeMonitor(monitor)}
        }
    }
}
