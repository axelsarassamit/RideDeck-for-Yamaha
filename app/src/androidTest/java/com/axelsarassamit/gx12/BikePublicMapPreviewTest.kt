package com.axelsarassamit.gx12

import android.graphics.*
import android.location.Location
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit opt-in network capability preview, never the rider's location or a hardware acceptance test. */
class BikePublicMapPreviewTest {
    @Test fun actualMapLibrePublicRouteAndProviderLanePreview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("phase1PublicMapPreview")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        check(c.packageName.endsWith(".preview"))
        NavigationSecrets.importDebugSetup(c)
        val cacheReady=CountDownLatch(1)
        instrumentation.runOnMainSync { MapLibre.getInstance(c);MapDiskCache.configure(c){cacheReady.countDown()} }
        assertTrue("Map cache startup",cacheReady.await(10,TimeUnit.SECONDS))
        check(TomTomApi.configured(c)&&NavigationSecrets.read(c,"maptiler").isNotBlank()){"Configure preview providers privately"}
        val origin=Location("public-fixture").apply{latitude=13.730;longitude=100.535}
        val route=TomTomApi.routes(c,origin,NavigationPlace("Public Bangkok fixture",13.740,100.560),false).first()
        val lane=route.lanes.firstOrNull{LaneManeuverBinding.associatedPoint(it,route.cumulative,route.turns.map{t->t.start},false)!=null}
            ?:error("No maneuver-aligned lanes returned for public fixture")
        val point=LaneManeuverBinding.associatedPoint(lane,route.cumulative,route.turns.map{it.start},false)!!
        val index=route.cumulative.indexOfLast{it<=route.cumulative[point]-200}.coerceIn(0,route.points.lastIndex-1)
        val position=route.points[index]
        val ahead=route.points[(index+4).coerceAtMost(route.points.lastIndex)]
        val bearing=Location("a").apply{latitude=position.latitude;longitude=position.longitude}.bearingTo(Location("b").apply{latitude=ahead.latitude;longitude=ahead.longitude}).toDouble()
        val turn=route.turns.single{it.start==point}
        val offline=InstrumentationRegistry.getArguments().getString("phase1MapOffline")=="true"
        // SDK-only override: never change the phone's Wi-Fi/mobile settings.
        if(offline)instrumentation.runOnMainSync{MapLibre.setConnected(false)}
        try {
        for(night in listOf(false,true))for(height in listOf(234,240)) {
            val layout=BikeNavigationLayout(height,true)
            val json=if(offline)checkNotNull(MapStyleCache.read(c,night,NavigationSecrets.read(c,"maptiler"))){"Warm style is unavailable"}
                else NavigationApi.englishMapStyle(c,night)
            val done=CountDownLatch(1);var failure:String?=null
            var activeRenderer:MapSnapshotter?=null
            instrumentation.runOnMainSync {
                MapLibre.getInstance(c)
                org.maplibre.android.log.Logger.setVerbosity(org.maplibre.android.log.Logger.NONE)
                // Match the live compositor: centre on the current fix so the rider remains visible.
                val camera=CameraPosition.Builder().target(position).zoom(16.8).bearing(bearing).tilt(55.0).build()
                val renderer=MapSnapshotter(c,MapSnapshotter.Options(layout.mapWidth,layout.mapHeight)
                    .withPixelRatio(1f).withLogo(false).withAttribution(false)
                    .withStyleBuilder(Style.Builder().fromJson(json)).withCameraPosition(camera))
                activeRenderer=renderer
                renderer.start({snapshot->
                    try {
                        val map=snapshot.bitmap.copy(Bitmap.Config.ARGB_8888,true)
                        val colours=mutableSetOf<Int>()
                        for(y in 0 until map.height step 3)for(x in 0 until map.width step 3)colours.add(map.getPixel(x,y))
                        assertTrue("Cached map must contain rendered detail, not an empty surface",colours.size>64)
                        val canvas=Canvas(map);val path=Path();val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                        route.points.drop(index).forEachIndexed{i,point->val pixel=snapshot.pixelForLatLng(point);if(i==0)path.moveTo(pixel.x,pixel.y)else path.lineTo(pixel.x,pixel.y)}
                        paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;paint.color=Color.WHITE;canvas.drawPath(path,paint)
                        paint.strokeWidth=5f;paint.color=AppearancePalette.accent(4,night);canvas.drawPath(path,paint)
                        val pixel=snapshot.pixelForLatLng(position)
                        YamahaPositionMarker.draw(c,canvas,pixel.x,pixel.y,0f)
                        for(phase in listOf(0L,10_000L)) {
                            val output=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
                            BikeNavigationRenderer.draw(Canvas(output),map,layout,
                                BikeNavigationState(turn.sign,route.cumulative[point]-route.cumulative[index],turn.road.ifBlank{turn.text},"Public route · DEMO","11:25",null,
                                    lanes=lane,fuelRange="Est. fuel range ~180 km",elapsedMs=phase),night)
                            val suffix=if(offline)"-offline" else ""
                            c.openFileOutput("public-map-$height-$night-$phase$suffix.png",0).use{output.compress(Bitmap.CompressFormat.PNG,100,it)}
                            output.recycle()
                        }
                        map.recycle()
                    } catch(e:Throwable){failure=e.javaClass.simpleName} finally {done.countDown()}
                }) { failure="Map snapshot failed";done.countDown() }
            }
            val finished=done.await(60,TimeUnit.SECONDS)
            instrumentation.runOnMainSync{activeRenderer?.cancel();activeRenderer=null}
            assertTrue("Public map snapshot timeout",finished)
            assertNull(failure)
        }
        } finally { if(offline)instrumentation.runOnMainSync{MapLibre.setConnected(null)} }
    }
}
