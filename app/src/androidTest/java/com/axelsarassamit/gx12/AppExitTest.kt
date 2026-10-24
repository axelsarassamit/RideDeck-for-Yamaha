package com.axelsarassamit.gx12

import android.app.ActivityManager
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Preview only: actual GPS service, no bike connection, route request or recording. */
class AppExitTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val c=i.targetContext
    private fun waitFor(message:String,condition:()->Boolean) {
        repeat(70){i.waitForIdleSync();if(condition())return;Thread.sleep(100)}
        fail(message)
    }
    private fun stopped() {
        waitFor("Navigation did not stop"){!NativeNavigation.running}
        waitFor("A ride foreground service survived exit") {
            c.getSystemService(ActivityManager::class.java).getRunningServices(100).none {
                it.service.className in listOf(NativeNavigationService::class.java.name,YamahaCastService::class.java.name,BikeDataService::class.java.name,DashRideService::class.java.name)
            }
        }
        assertTrue(RideAppExit.closed(c));assertFalse(RideBackgroundSession.navigationActive(c))
        assertNull(RideBackgroundSession.castAddress(c));assertTrue(YamahaCastService.autoReconnectPaused)
    }
    private fun isolated(block:()->Unit) {
        check(c.packageName.endsWith(".preview"))
        val prefs=RidePreferences.prefs(c);val keys=listOf("map_auto","map_startup");val old=keys.associateWith{prefs.all[it]}
        val lifecycle=c.getSharedPreferences("app_lifecycle",0);val closed=lifecycle.getBoolean("closed",false)
        prefs.edit().putBoolean("map_auto",false).putBoolean("map_startup",false).commit()
        try{block()}finally {
            i.runOnMainSync{RideAppExit.stop(c)}
            prefs.edit().apply{for((k,v) in old)if(v is Boolean)putBoolean(k,v)else remove(k)}.commit()
            lifecycle.edit().putBoolean("closed",closed).commit()
        }
    }
    private fun start(s:ActivityScenario<MainActivity>) {
        s.onActivity{NativeNavigation.start(it)}
        waitFor("GPS service did not start"){NativeNavigation.running}
    }
    @Test fun rootBackStopsServicesAndClearsResumeState()=isolated {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            start(s)
            RideBackgroundSession.castStarted(c,"fixture-not-a-device",false,"fixture")
            s.onActivity{assertTrue(it.isTaskRoot)}
            i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            stopped()
        }
    }
    @Test fun removingActualTaskStopsServices()=isolated {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            start(s)
            s.onActivity{a->a.getSystemService(ActivityManager::class.java).appTasks.first{it.taskInfo.baseIntent.component?.packageName==c.packageName}.finishAndRemoveTask()}
            stopped()
        }
    }
    @Test fun backgroundAndRotationDoNotCountAsClosing()=isolated {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            start(s)
            s.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(300);assertTrue(NativeNavigation.running);assertFalse(RideAppExit.closed(c))
            s.moveToState(Lifecycle.State.RESUMED);s.recreate()
            assertTrue(NativeNavigation.running);assertFalse(RideAppExit.closed(c))
            s.onActivity{it.onBackPressed()};stopped()
        }
    }
    @Test fun queuedStartCannotReviveClosedAppAndReopeningRestoresNavigation()=isolated {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            start(s)
            s.onActivity{RideAppExit.stop(it)};stopped()
            s.onActivity{it.startForegroundService(Intent(it,NativeNavigationService::class.java))}
            Thread.sleep(500);stopped()
            s.recreate();assertFalse(RideAppExit.closed(c));assertFalse(YamahaCastService.autoReconnectPaused);start(s)
            s.onActivity{it.onBackPressed()};stopped()
        }
    }
}
