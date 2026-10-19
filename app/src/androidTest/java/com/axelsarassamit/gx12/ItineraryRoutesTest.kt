package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import app.pillion.core.*
class ItineraryRoutesTest {
    @Test fun publicBangkokProviderLoopParsesEveryLegAndFinalArrival() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val text=instrumentation.context.assets.open("bangkok_public_loop.json").bufferedReader().use{it.readText()}
        val route=NavigationApi.parseRoutes(org.json.JSONObject(text),NavigationPlace("Public Bangkok loop",13.7279,100.5241)).single()
        assertEquals(3,route.stopIndices.size)
        assertTrue(route.stopIndices.zipWithNext().all{it.first<it.second})
        assertEquals(route.points.lastIndex,route.stopIndices.last())
        assertEquals(1,route.turns.count{it.sign==5})
        assertTrue(route.turns.all{it.start in route.points.indices && it.end in it.start..route.points.lastIndex})
        assertEquals(15085.0,route.meters,1.0)
        assertTrue(route.millis in 2_250_000L..2_270_000L)
    }
    private val a=NavigationPlace("A",13.0,100.0)
    private val b=NavigationPlace("B",13.001,100.001)
    private val c=NavigationPlace("C",13.002,100.002)
    private fun part(from:NavigationPlace,to:NavigationPlace)=NavigationRoute(to,listOf(LatLng(from.latitude,from.longitude),LatLng(to.latitude,to.longitude)),listOf(NavigationTurn("Continue",0,0,1,"",10000),NavigationTurn("Arrive",5,1,1)),100.0,10000,lanes=listOf(LaneSection(0,1,listOf(GuidanceLane(listOf(0),listOf(0))))))
    @Test fun mergesLegsAndOffsetsLanesAndTurns() {
        val r=ItineraryRoutes.combine(c,listOf(part(a,b),part(b,c)))
        assertEquals(3,r.points.size);assertEquals(listOf(1,2),r.stopIndices)
        assertEquals(200.0,r.meters,0.01);assertEquals(20000L,r.millis)
        assertEquals(1,r.lanes[1].start);assertEquals(2,r.lanes[1].end)
        assertEquals(1,r.turns.count{it.sign==5});assertTrue(r.turns.all{it.start in r.points.indices && it.end in r.points.indices})
    }
    @Test fun remainingStopsNeverRetiresFinalDestination() {
        val r=part(a,c).copy(itinerary=listOf(b,c),stopIndices=listOf(10,20))
        assertEquals(listOf(b,c),ItineraryRoutes.remaining(r,9));assertEquals(listOf(c),ItineraryRoutes.remaining(r,13));assertEquals(listOf(c),ItineraryRoutes.remaining(r,200))
    }
    @Test fun itineraryRoundTripAndMalformedInput() {
        assertEquals(listOf(b,c),ItineraryRoutes.decode(ItineraryRoutes.encode(listOf(b,c))))
        assertTrue(ItineraryRoutes.decode("[{\"lat\":999,\"lon\":0}]").isEmpty());assertTrue(ItineraryRoutes.decode("bad").isEmpty())
    }
    @Test fun destinationAndStopsPersistTogetherAndPlainDestinationClearsOldStops() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        try {
            RideBackgroundSession.navigationStarted(context)
            RideBackgroundSession.saveDestination(context,c,listOf(b,c),"winding")
            assertEquals(c,RideBackgroundSession.destination(context))
            assertEquals(listOf(b,c),RideBackgroundSession.itinerary(context))
            assertEquals("winding",RideBackgroundSession.rideStyle(context))
            RideBackgroundSession.saveDestination(context,a)
            assertEquals(a,RideBackgroundSession.destination(context))
            assertTrue(RideBackgroundSession.itinerary(context).isEmpty())
            assertEquals("relaxed",RideBackgroundSession.rideStyle(context))
            RideBackgroundSession.navigationStopped(context)
            assertNull(RideBackgroundSession.destination(context))
        } finally { RideBackgroundSession.navigationStopped(context) }
    }

    @Test fun tomTomSharedBoundaryUsesProviderIndexConvention() {
        fun point(p:NavigationPlace)=org.json.JSONObject().put("latitude",p.latitude).put("longitude",p.longitude)
        for(lastIndex in listOf(2,3)) {
            val legs=org.json.JSONArray().put(org.json.JSONObject().put("points",org.json.JSONArray().put(point(a)).put(point(b))))
                .put(org.json.JSONObject().put("points",org.json.JSONArray().put(point(b)).put(point(c))))
            val instructions=org.json.JSONArray().put(org.json.JSONObject().put("pointIndex",0).put("maneuver","DEPART"))
                .put(org.json.JSONObject().put("pointIndex",1).put("maneuver","ARRIVE"))
                .put(org.json.JSONObject().put("pointIndex",lastIndex).put("maneuver","ARRIVE"))
            val raw=org.json.JSONObject().put("legs",legs).put("guidance",org.json.JSONObject().put("instructions",instructions))
                .put("summary",org.json.JSONObject().put("travelTimeInSeconds",20).put("lengthInMeters",200))
            val r=TomTomApi.parseRoutes(org.json.JSONObject().put("routes",org.json.JSONArray().put(raw)),c).single()
            assertEquals(lastIndex,r.points.lastIndex);assertEquals(listOf(1,lastIndex),r.stopIndices)
            assertEquals(lastIndex,r.turns.last().start);assertEquals(1,r.turns.count{it.sign==5})
        }
    }
    @Test fun tomTomTrafficSideReachesTheDisplayedUturn() {
        for ((side, sign) in listOf("LEFT" to 8, "RIGHT" to -8)) {
            val points=org.json.JSONArray().put(org.json.JSONObject().put("latitude",13.0).put("longitude",100.0))
                .put(org.json.JSONObject().put("latitude",13.001).put("longitude",100.0))
            val instructions=org.json.JSONArray().put(org.json.JSONObject().put("pointIndex",0).put("maneuver","MAKE_UTURN").put("drivingSide",side))
                .put(org.json.JSONObject().put("pointIndex",1).put("maneuver","ARRIVE"))
            val route=org.json.JSONObject().put("legs",org.json.JSONArray().put(org.json.JSONObject().put("points",points)))
                .put("guidance",org.json.JSONObject().put("instructions",instructions))
                .put("summary",org.json.JSONObject().put("travelTimeInSeconds",20).put("lengthInMeters",100))
            val parsed=TomTomApi.parseRoutes(org.json.JSONObject().put("routes",org.json.JSONArray().put(route)),c).single()
            assertEquals(sign,parsed.turns.first().sign)
        }
    }
    @Test fun joinedSpeedLimitsKeepEdgePositionsAndUnknowns() {
        val first=part(a,b).copy(speedLimits=intArrayOf(50))
        val second=part(b,c).copy(speedLimits=intArrayOf(80))
        assertArrayEquals(intArrayOf(50,80),ItineraryRoutes.combine(c,listOf(first,second)).speedLimits)
        assertArrayEquals(intArrayOf(50,0),ItineraryRoutes.combine(c,listOf(first,second.copy(speedLimits=null))).speedLimits)
        assertNull(ItineraryRoutes.combine(c,listOf(first.copy(speedLimits=null),second.copy(speedLimits=null))).speedLimits)
    }
}
