package com.axelsarassamit.gx12

import android.graphics.*
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapNavigationTest {
    @Test fun mapControlsCannotCoverDirectionsOrMapPixelsAtBothPhoneSizes() {
        ActivityScenario.launch(DestinationSearchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for(size in listOf(1080 to 700,700 to 600)) {
                    val panel=NavigationUi.panel(activity)
                    panel.measure(View.MeasureSpec.makeMeasureSpec(size.first,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size.second,View.MeasureSpec.EXACTLY))
                    panel.layout(0,0,size.first,size.second)
                    val all=ReleaseMenuTest.views(panel)
                    val image=all.filterIsInstance<ImageView>().single()
                    fun bounds(view:View)=Rect().also { view.getDrawingRect(it);(panel as android.view.ViewGroup).offsetDescendantRectToMyCoords(view,it) }
                    val mapBounds=bounds(image)
                    assertTrue("Map must remain usable",mapBounds.width()>size.first/2 && mapBounds.height()>size.second/3)
                    all.filterIsInstance<Button>().forEach { button ->
                        assertFalse("Zoom covers map",Rect.intersects(mapBounds,bounds(button)))
                        assertTrue(button.height>=63*activity.resources.displayMetrics.density)
                    }
                    val directions=all.single{it.contentDescription=="Next navigation instruction"}
                    assertFalse("Directions overlap map",Rect.intersects(mapBounds,bounds(directions)))
                }
            }
        }
    }
    @Test fun bikeGuidanceAndArrivalStayClearOfFirmwareZoomStrip() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(c.getExternalFilesDir("tests"),"map39-previews").apply{mkdirs()}
        for(height in listOf(234,240)) {
            val l=BikeNavigationLayout(height)
            val map=Bitmap.createBitmap(l.mapWidth,l.mapHeight,Bitmap.Config.ARGB_8888)
            val m=Canvas(map);m.drawColor(0xffbec8ba.toInt());val paint=Paint().apply{color=Color.WHITE;strokeWidth=5f}
            for(x in 0..348 step 35)m.drawLine(x.toFloat(),0f,x+120f,l.mapHeight.toFloat(),paint)
            paint.color=0xffdb35e7.toInt();paint.strokeWidth=7f;m.drawLine(174f,l.mapHeight.toFloat(),174f,0f,paint)
            val image=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image)
            BikeNavigationRenderer.draw(canvas,map,l,BikeNavigationState(-2,150.0,"Sukhumvit Road","Thepharak Road","11:25",80),false)
            // Simulate the firmware overlay only in a preview; never send button frames to a real bike.
            paint.color=Color.DKGRAY;canvas.drawRect(0f,64f,44f,152f,paint)
            paint.color=Color.WHITE;paint.textSize=30f;canvas.drawText("+",9f,98f,paint);canvas.drawText("−",9f,139f,paint)
            assertEquals(348,l.panelLeft)
            assertTrue(l.speedX+l.speedRadius<l.panelLeft)
            File(folder,"bike-$height.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle();map.recycle()
        }
    }
    @Test fun liveRoutingReturnsUsableMotorcycleAlternativesForPublicBangkokLocations() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val origin=android.location.Location("test-request").apply { latitude=13.7308;longitude=100.5699 }
        val routes=TomTomApi.routes(c,origin,NavigationPlace("Public route check",13.7563,100.5018),true)
        assertTrue(routes.size in 1..3)
        assertEquals(routes.minOf{it.millis},routes.first().millis)
        assertTrue(routes.all{it.points.size>=2 && it.meters>0 && it.millis>0})
    }
}
