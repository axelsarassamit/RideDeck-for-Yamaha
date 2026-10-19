package com.axelsarassamit.gx12

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: sends public fixture coordinates, never starts navigation. */
class FunRideLiveTest {
    @Test fun publicBangkokFindRidesOpensActualRoadOverview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveNetwork")=="true")
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".preview"))
        val p=RidePreferences.prefs(c);val old=p.all["tomtom_routing"];val keys=c.getSharedPreferences("navigation_keys",0);val oldKey=keys.getString("tomtom",null)
        val reject=InstrumentationRegistry.getArguments().getString("rejectTomTom")=="true"
        val tomtom=InstrumentationRegistry.getArguments().getString("useTomTom")=="true"
        if(tomtom)NavigationSecrets.importDebugSetup(c)
        p.edit().putBoolean("tomtom_routing",reject || tomtom).commit()
        if(reject)NavigationSecrets.save(c,"tomtom","invalid-test-key")
        val monitor=i.addMonitor(RoutePreviewActivity::class.java.name,null,false)
        try {
            ActivityScenario.launch(FunRideActivity::class.java).use { s ->
                s.onActivity { a ->
                    FunRideActivity::class.java.getDeclaredField("startPlace").apply{isAccessible=true}.set(a,NavigationPlace("Public Bangkok test start",13.7513,100.5018))
                    if(InstrumentationRegistry.getArguments().getString("winding")=="true")
                        SharedSetupTest.views(a.window.decorView).filterIsInstance<android.widget.Spinner>()[1].setSelection(1)
                    SharedSetupTest.views(a.window.decorView).filterIsInstance<Button>().single{it.text=="Find rides"}.performClick()
                }
                val preview=i.waitForMonitorWithTimeout(monitor,120000)
                assertNotNull("No actual road overview returned",preview)
                i.waitForIdleSync()
                i.runOnMainSync{SharedSetupTest.shot(preview!!,"live-bangkok-rides");preview.finish()}
            }
        } finally {
            i.removeMonitor(monitor)
            keys.edit().apply{if(oldKey==null)remove("tomtom")else putString("tomtom",oldKey)}.commit()
            p.edit().apply{if(old is Boolean)putBoolean("tomtom_routing",old)else remove("tomtom_routing")}.commit()
        }
    }
}
