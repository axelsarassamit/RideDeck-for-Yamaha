package com.axelsarassamit.gx12

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import app.pillion.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures only: no network, route start, bike or saved production locations. */
class RouteGuidanceTest {
    @Test fun providerLanesRejectUnknownRecommendationsAndPreserveAllPositions() {
        val valhalla=LaneData.valhalla(JSONArray("[{\"directions\":8,\"active\":8},{\"directions\":10,\"valid\":8}]"))
        assertEquals(listOf(-2),valhalla[0].preferred)
        assertEquals(listOf(-2),valhalla[1].valid)
        val bad=LaneData.valhalla(JSONArray("[{\"directions\":2,\"active\":8}]"))
        assertTrue(bad.single().preferred.isEmpty())
        assertTrue(LaneData.valhalla(JSONArray("[{\"directions\":8,\"active\":8},null,{\"directions\":2}]")).isEmpty())
        val sections=LaneData.tomTom(JSONArray("[{\"sectionType\":\"LANES\",\"startPointIndex\":1,\"endPointIndex\":2,\"lanes\":[{\"directions\":[\"LEFT\"],\"follow\":\"LEFT\"},{\"directions\":[\"STRAIGHT\"]}]}]"),3)
        assertEquals(1,sections.size)
        assertEquals("Use the left lane.",sections.single().spoken())
        assertTrue(LaneData.tomTom(JSONArray("[{\"sectionType\":\"LANES\",\"startPointIndex\":1,\"endPointIndex\":20,\"lanes\":[]}]"),3).isEmpty())
    }
    @Test fun roadPreferencesAffectBothRequestsAndReturnedRouteFiltering() {
        val point=JSONObject().put("latitude",13.0).put("longitude",100.0)
        val end=JSONObject().put("latitude",13.001).put("longitude",100.001)
        val route=JSONObject().put("legs",JSONArray().put(JSONObject().put("points",JSONArray().put(point).put(end))))
            .put("summary",JSONObject().put("travelTimeInSeconds",20).put("lengthInMeters",150))
            .put("guidance",JSONObject().put("instructions",JSONArray().put(JSONObject().put("pointIndex",1).put("maneuver","ARRIVE"))))
            .put("sections",JSONArray().put(JSONObject().put("sectionType","FERRY")))
        val response=JSONObject().put("routes",JSONArray().put(route))
        val target=NavigationPlace("Fixture",13.001,100.001)
        assertEquals(1,TomTomApi.parseRoutes(response,target,RoadPreferences(avoidFerries=false)).size)
        try { TomTomApi.parseRoutes(response,target,RoadPreferences(avoidFerries=true));fail("Avoided ferry route accepted") }
        catch(_:IllegalStateException) { }
    }
    @Test fun bikeLaneAndEstimatedRangePreviewRenderInsideMapArea() {
        val bitmap=Bitmap.createBitmap(480,234,Bitmap.Config.ARGB_8888)
        val map=Bitmap.createBitmap(348,196,Bitmap.Config.ARGB_8888)
        val section=LaneSection(0,1,listOf(GuidanceLane(listOf(-8),listOf(-8)),GuidanceLane(listOf(0)),GuidanceLane(listOf(2))))
        BikeNavigationRenderer.draw(Canvas(bitmap),map,BikeNavigationLayout(234),
            BikeNavigationState(-8,150.0,"U-turn","Fixture road","11:25",60,lanes=section,fuelRange="Est. fuel range ~180 km"),false)
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(c.packageName.endsWith(".preview"))
        c.openFileOutput("lane-fuel-preview.png",0).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        assertNotEquals(0,bitmap.getPixel(58,10))
        assertNotEquals(0,bitmap.getPixel(10,175))
        bitmap.recycle();map.recycle()
    }
}
