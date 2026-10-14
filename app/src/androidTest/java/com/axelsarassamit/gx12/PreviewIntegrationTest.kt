package com.axelsarassamit.gx12

import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.pillion.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic records are strictly restricted to the separate preview package. */
class PreviewIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val c get() = instrumentation.targetContext
    @Before fun isolatedPackageOnly() {
        assumeTrue(BuildConfig.DEBUG && c.packageName.endsWith(".preview"))
        ReleaseMenuTest.importProviderSetup()
    }
    private fun message(server: Int = 1248): CcuProtocol.Message {
        val raw = ByteArray(33).apply { this[0]=6; this[1]=0x40; this[2]=42; this[6]=190.toByte(); this[8]=128.toByte(); this[14]=80; this[15]=66 }
        return CcuProtocol.Message(0x10f, byteArrayOf(0x18, (server % 128 or 128).toByte(), (server / 128).toByte(), 0x22,37,8,1,0x12,33)+raw)
    }
    private fun records(): List<RideRecord> {
        val latch = CountDownLatch(1); var result = emptyList<RideRecord>()
        RideBookStore.records(c) { result=it; latch.countDown() }
        check(latch.await(10,TimeUnit.SECONDS)); return result
    }
    @Test fun oldRideHistoryMigratesAndBikeDataRetainsSourceAndAge() {
        c.deleteDatabase("yamaha-rides.db")
        c.getDatabasePath("yamaha-rides.db").parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(c.getDatabasePath("yamaha-rides.db"), null).use { db ->
            db.execSQL("CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER NOT NULL, state TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, meters REAL NOT NULL DEFAULT 0, speed REAL, speed_at INTEGER, accel REAL, accel_at INTEGER, decel REAL, decel_at INTEGER, lean REAL, lean_at INTEGER)")
            db.execSQL("CREATE TABLE samples (ride INTEGER NOT NULL,time INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,speed REAL NOT NULL,accuracy REAL NOT NULL,lean REAL)")
            db.execSQL("INSERT INTO rides(started,state,speed,speed_at) VALUES(1000,'Ended',12.5,2000)")
            db.version=1
        }
        assertEquals(12.5, records().single().peaks.getValue("speed").first, 0.0)
        RideBookStore.start(c); records()
        BikeDataStore.start(c); BikeDataStore.status("Preview fixture", true)
        BikeDataStore.accept(c,message())
        assertEquals(1600.0,BikeDataStore.state.values.getValue(BikeMetric.RPM).value,0.0)
        val previous=BikeDataStore.state
        BikeDataStore.accept(c,message(1249)); assertEquals(previous,BikeDataStore.state)
        BikeDataStore.mark(c,"front_on")
        val ride=records().first(); assertEquals(1600.0,ride.bike.getValue("RPM").maximum,0.0)
        assertTrue(ride.bike.getValue("RPM").source.startsWith("Yamaha CCU"))
        // A common-data VIN local must never be admitted to the private log or raw record table.
        BikeDataStore.accept(c,CcuProtocol.Message(0x10b,byteArrayOf(0x18,0xe0.toByte(),9,0x22,8,8,0x90.toByte(),0xe3.toByte(),3,0x12,2,65,66)))
        assertFalse(BikeDataStore.state.raw.keys.any { it.contains("61840") })
        BikeDataStore.stop(c,"Preview disconnected")
        assertFalse(BikeDataStore.state.active); assertTrue(BikeDataStore.state.values.isNotEmpty())
        RideBookStore.pause(c,true); records()
        SQLiteDatabase.openDatabase(c.getDatabasePath("yamaha-rides.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT source,event FROM bike_events",null).use { q -> assertTrue(q.moveToFirst()); assertEquals("user_observation",q.getString(0)); assertEquals("front_on",q.getString(1)) }
            assertEquals(2,db.version)
        }
    }
    @Test fun motorwayRejectedAndTrafficUturnAndSpeedLimitPreserved() {
        val route=JSONObject("""{"summary":{"travelTimeInSeconds":120,"lengthInMeters":500,"trafficDelayInSeconds":20},"legs":[{"points":[{"latitude":13.6,"longitude":100.5},{"latitude":13.601,"longitude":100.501}]}],"guidance":{"instructions":[{"pointIndex":0,"travelTimeInSeconds":0,"maneuver":"MAKE_U_TURN","message":"Make a U-turn"},{"pointIndex":1,"travelTimeInSeconds":120,"maneuver":"ARRIVE"}]},"sections":[{"sectionType":"SPEED_LIMIT","startPointIndex":0,"endPointIndex":1,"maxSpeedLimitInKmh":50}]}""")
        val target=NavigationPlace("Test pin",13.601,100.501)
        val parsed=TomTomApi.parseRoutes(JSONObject().put("routes",org.json.JSONArray().put(route)),target).single()
        assertEquals(-8,parsed.turns.first().sign); assertEquals(20000L,parsed.trafficDelayMillis); assertEquals(50,parsed.speedLimits!![0])
        route.getJSONArray("sections").put(JSONObject().put("sectionType","MOTORWAY"))
        try { TomTomApi.parseRoutes(JSONObject().put("routes",org.json.JSONArray().put(route)),target); fail("Motorway accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun configuredSearchFindsTheRequestedHomeAndStoresAndFuelStations() {
        assumeTrue(TomTomApi.configured(c))
        val origin=Location("preview").apply { latitude=13.646;longitude=100.592;accuracy=5f;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos() }
        val home=TomTomApi.search(c,"Niche mono Pu Chao, Sukhumvit Rd., Thepharak, Mueng Samut Prakan 10270",origin)
        assertTrue("Home name missing",home.any { it.label.contains("niche",true) })
        val stores=TomTomApi.search(c,"CentralWorld Bangkok",origin)
        assertTrue("Store search returned no named places",stores.any { it.kind=="poi" && it.label.contains("central",true) })
        assertTrue("No nearby stations returned",TomTomApi.stations(c,origin.latitude,origin.longitude).isNotEmpty())
    }
    @Test fun repeatedZoomPublishesTheLatestCameraRequest() {
        assumeTrue(NavigationSecrets.read(c,"maptiler").isNotBlank())
        ActivityScenario.launch(DestinationSearchActivity::class.java).use { scenario ->
            scenario.onActivity {
                NativeNavigation.attach(c)
                NativeNavigation.location=Location("preview").apply { latitude=13.646;longitude=100.592;accuracy=5f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos() }
                NativeNavigation.zoom(true)
            }
            try {
                val until=SystemClock.elapsedRealtime()+45000
                while(NativeNavigation.mapBitmap==null && SystemClock.elapsedRealtime()<until) Thread.sleep(250)
                assertNotNull("Initial map did not render: ${NativeNavigation.status}",NativeNavigation.mapBitmap)
                repeat(12) { NativeNavigation.zoom(it%2==0);Thread.sleep(35) }
                instrumentation.waitForIdleSync()
                val requested=NativeNavigation.zoomState().revision
                val field=NativeNavigation::class.java.getDeclaredField("renderedZoomRevision").apply { isAccessible=true }
                val end=SystemClock.elapsedRealtime()+30000
                while(field.getLong(NativeNavigation)!=requested && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
                assertEquals(requested,field.getLong(NativeNavigation))
                assertFalse(NativeNavigation.status.contains("unavailable",true))
            } finally { instrumentation.runOnMainSync { NativeNavigation.detach() } }
        }
    }
}
