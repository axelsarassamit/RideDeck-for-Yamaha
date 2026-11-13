package com.axelsarassamit.gx12

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastMapFrameTest {
    @Test fun staleGpsCannotLeaveRecentlyRenderedGuidanceMarkedLive() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".preview"))
        assertFalse("Run this isolated check without an active preview ride",NativeNavigation.running)
        fun field(name:String)=NativeNavigation::class.java.getDeclaredField(name).apply{isAccessible=true}
        val bikeField=field("bikeMapFrame");val phoneField=field("phoneMapFrame");val contextField=field("context")
        val oldBike=bikeField.get(null);val oldPhone=phoneField.get(null);val oldContext=contextField.get(null)
        val oldLocation=NativeNavigation.location;val oldStatus=NativeNavigation.status
        val tick=field("tick").get(null) as Runnable
        val main=android.os.Handler(android.os.Looper.getMainLooper())
        val map=Bitmap.createBitmap(480,234,Bitmap.Config.ARGB_8888)
        val captured=android.os.SystemClock.elapsedRealtime()
        val bike=NativeNavigation.MapFrame(map,map,captured){byteArrayOf(1)}
        val phone=NativeNavigation.MapFrame(map,map,captured){byteArrayOf(1)}
        try {
            instrumentation.runOnMainSync {
                contextField.set(null,instrumentation.targetContext);bikeField.set(null,bike);phoneField.set(null,phone)
                NativeNavigation.running=true
                NativeNavigation.location=android.location.Location("fixture").apply {
                    latitude=13.0;longitude=100.0;accuracy=5f
                    elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()-10_000_000_001L
                }
                tick.run()
            }
            assertTrue("Bike frame must stop showing live guidance as soon as GPS expires",bike.invalidated)
            assertTrue("Phone frame must stop showing live guidance as soon as GPS expires",phone.invalidated)
        } finally {
            instrumentation.runOnMainSync {
                main.removeCallbacks(tick);NativeNavigation.running=false;NativeNavigation.location=oldLocation;NativeNavigation.status=oldStatus
                contextField.set(null,oldContext);bikeField.set(null,oldBike);phoneField.set(null,oldPhone)
            }
            map.recycle()
        }
    }
    @Test fun clearingRouteKeepsSessionButImmediatelyInvalidatesOldGuidance() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".preview"))
        val frameField=NativeNavigation::class.java.getDeclaredField("bikeMapFrame").apply{isAccessible=true}
        val contextField=NativeNavigation::class.java.getDeclaredField("context").apply{isAccessible=true}
        val oldFrame=frameField.get(null);val oldContext=contextField.get(null)
        val oldRunning=NativeNavigation.running;val oldRoute=NativeNavigation.route;val oldStatus=NativeNavigation.status
        val map=Bitmap.createBitmap(480,234,Bitmap.Config.ARGB_8888)
        val frame=NativeNavigation.MapFrame(map,map,android.os.SystemClock.elapsedRealtime()){byteArrayOf(1)}
        try {
            instrumentation.runOnMainSync {
                contextField.set(null,null) // Never clear a persisted destination as a test side effect.
                frameField.set(null,frame);NativeNavigation.running=true
                val points=listOf(org.maplibre.android.geometry.LatLng(13.0,100.0),org.maplibre.android.geometry.LatLng(13.001,100.001))
                NativeNavigation.route=NavigationRoute(NavigationPlace("Fixture",13.001,100.001),points,emptyList(),150.0,20_000)
                NativeNavigation.stopRoute()
            }
            instrumentation.waitForIdleSync()
            assertNull(NativeNavigation.route)
            assertTrue("Clear route must keep the map session",NativeNavigation.running)
            assertTrue("Old turn/lane overlays must stop being live immediately",frame.invalidated)
        } finally {
            instrumentation.runOnMainSync {
                frameField.set(null,oldFrame);contextField.set(null,oldContext)
                NativeNavigation.running=oldRunning;NativeNavigation.route=oldRoute
                NativeNavigation.status=oldStatus
            }
            map.recycle()
        }
    }
    @Test fun delayedFrameKeepsRoadsWithoutReplayingOldOverlays() {
        val base = Bitmap.createBitmap(360, 190, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val live = Bitmap.createBitmap(480, 234, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val frame = NativeNavigation.MapFrame(live, base, 1000) { byteArrayOf(it.width.toByte()) }

        assertTrue(frame.fresh(10999))
        assertFalse(frame.fresh(11000))
        assertEquals(live.width, frame.lastMap.width)
        assertEquals(live.height, frame.lastMap.height)
        assertEquals(Color.GREEN, frame.lastMap.getPixel(240, 117))
        assertNotEquals(Color.GREEN, frame.lastMap.getPixel(0, 0))
        assertSame(frame.lastJpeg, frame.lastJpeg)

        frame.invalidated = true
        assertFalse(frame.fresh(1001))
    }
}
