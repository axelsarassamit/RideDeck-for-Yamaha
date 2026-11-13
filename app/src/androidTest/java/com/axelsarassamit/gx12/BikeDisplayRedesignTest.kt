package com.axelsarassamit.gx12

import android.graphics.*
import app.pillion.core.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class BikeDisplayRedesignTest {
    @Test fun unavailableGuidanceCannotDisplayAnOldTurnArrow() {
        for(night in listOf(false,true))for(height in listOf(234,240)) {
            val layout=BikeNavigationLayout(height)
            val map=Bitmap.createBitmap(layout.mapWidth,layout.mapHeight,Bitmap.Config.ARGB_8888)
            val output=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
            for(notice in listOf("Waiting for GPS","Recalculating","Choose destination")) {
                BikeNavigationRenderer.draw(Canvas(output),map,layout,
                    BikeNavigationState(2,null,"Old road","",null,null,notice),night)
                for(y in 5..48)for(x in layout.panelLeft+8..470)
                    assertEquals("No live turn arrow during $notice",AppearancePalette.panel(night),output.getPixel(x,y))
            }
            output.recycle();map.recycle()
        }
    }
    @Test fun separateJunctionCannotSqueezeOrPaintOverMapOrFixedGuidance() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview"))
        val section=LaneSection(0,1,listOf(GuidanceLane(listOf(-2),listOf(-2)),GuidanceLane(listOf(-2),listOf(-2)),GuidanceLane(listOf(0))))
        for(height in listOf(234,240))for(night in listOf(false,true)) {
            val layout=BikeNavigationLayout(height,true)
            val map=Bitmap.createBitmap(layout.mapWidth,layout.mapHeight,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.MAGENTA)}
            val output=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
            BikeNavigationRenderer.draw(Canvas(output),map,layout,
                BikeNavigationState(-2,150.0,"Coastal Road","Fixture road","11:25",null,lanes=section),night)
            assertEquals("Map retains its whole central viewport",Color.MAGENTA,output.getPixel(layout.mapLeft+20,layout.mapHeight-35))
            assertNotEquals("Junction has its own left surface",Color.MAGENTA,output.getPixel(70,60))
            assertNotEquals("Guidance retains its own right surface",Color.MAGENTA,output.getPixel(470,100))
            c.openFileOutput("junction-fixture-$height-$night.png",0).use{output.compress(Bitmap.CompressFormat.PNG,100,it)}
            output.recycle();map.recycle()
        }
    }
}
