package com.axelsarassamit.gx12

import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.geometry.LatLng

/** Isolated preview only. No network request, route start, bike connection or production places. */
class FunRideFlowTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
    private fun fixture()=NavigationRoute(NavigationPlace("Fixture finish",13.001,100.001),listOf(LatLng(13.0,100.0),LatLng(13.001,100.001)),listOf(NavigationTurn("Continue",0,0,1,"Fixture",60000)),1000.0,60000)
    @Test fun plannerChoicesSurviveRotationAndBackDoesNotReplaceRoute() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview"))
        val old=NativeNavigation.route
        val running=fixture()
        RideBackgroundSession.navigationStopped(c)
        NativeNavigation.route=running
        try {
            ActivityScenario.launch(FunRideActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    val spinners=views(a.window.decorView).filterIsInstance<Spinner>()
                    assertEquals(3,spinners.size)
                    spinners[0].setSelection(3);spinners[1].setSelection(1);spinners[2].setSelection(2)
                }
                scenario.recreate()
                scenario.onActivity { a ->
                    val spinners=views(a.window.decorView).filterIsInstance<Spinner>()
                    assertEquals(listOf(3,1,2),spinners.map{it.selectedItemPosition})
                    assertSame(running,NativeNavigation.route)
                    views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()=="Back"}.performClick()
                    assertSame(running,NativeNavigation.route)
                }
            }
        } finally { NativeNavigation.route=old;RideBackgroundSession.navigationStopped(c) }
    }
    @Test fun calculatedPreviewRetainsChoicesAcrossRotationWithoutStartingThem() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview"))
        val route=fixture()
        val origin=Location("fixture").apply{latitude=13.0;longitude=100.0;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos();accuracy=5f}
        val old=NativeNavigation.route
        FunRidePreview.put(listOf(route),origin,60)
        val intent=Intent(c,RoutePreviewActivity::class.java).putExtra("funRide",true).putExtra("lat",route.destination.latitude).putExtra("lon",route.destination.longitude).putExtra("label",route.destination.label)
        ActivityScenario.launch<RoutePreviewActivity>(intent).use { scenario ->
            scenario.recreate()
            scenario.onActivity { a ->
                val labels=views(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
                assertTrue(labels.any{it.contains("Target 60 min")})
                assertTrue(labels.any{it.contains("Fixture finish")})
                assertSame(old,NativeNavigation.route)
            }
        }
    }
}
