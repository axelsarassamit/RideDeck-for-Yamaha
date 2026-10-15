package com.axelsarassamit.gx12

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TrafficAlertsTest {
    @Test fun parserRejectsExpiredFutureUncertainAndMalformedReports() {
        val now=java.time.Instant.parse("2026-10-15T00:00:00Z").toEpochMilli()
        val fixture="""{"incidents":[
          {"geometry":{"type":"Point","coordinates":[100.5,13.7]},"properties":{"id":"valid","iconCategory":1,"timeValidity":"present","probabilityOfOccurrence":"certain"}},
          {"geometry":{"type":"LineString","coordinates":[[100.5,13.7],[100.51,13.7]]},"properties":{"id":"closed","iconCategory":8,"timeValidity":"present","probabilityOfOccurrence":"probable"}},
          {"geometry":{"type":"Point","coordinates":[100.5,13.7]},"properties":{"id":"future","iconCategory":8,"timeValidity":"future","probabilityOfOccurrence":"certain"}},
          {"geometry":{"type":"Point","coordinates":[100.5,13.7]},"properties":{"id":"expired","iconCategory":8,"timeValidity":"present","probabilityOfOccurrence":"certain","endTime":"2026-10-14T00:00:00Z"}},
          {"geometry":{"type":"Point","coordinates":[100.5,13.7]},"properties":{"id":"uncertain","iconCategory":1,"timeValidity":"present","probabilityOfOccurrence":"improbable"}},
          {"geometry":{"type":"Point","coordinates":[100.5,95]},"properties":{"id":"invalid","iconCategory":1,"timeValidity":"present","probabilityOfOccurrence":"certain"}}
        ]}"""
        assertEquals(listOf("valid","closed"),TomTomApi.parseRouteIncidents(JSONObject(fixture),now).map{it.id})
    }
    @Test fun alertGraphicsKeepTurnPanelSpeedSignRoadAndCreditsVisible() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(c.getExternalFilesDir("tests"),"incident-previews").apply{mkdirs()}
        for(height in listOf(234,240))for(kind in IncidentKind.entries){
            val layout=BikeNavigationLayout(height)
            val map=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(map);canvas.drawColor(0xffcdd4cc.toInt());val paint=Paint().apply{color=Color.WHITE;strokeWidth=5f}
            for(x in 0..480 step 35)canvas.drawLine(x.toFloat(),0f,(x+120).toFloat(),height.toFloat(),paint)
            val state=BikeNavigationState(-2,150.0,"Oak Road","Main Road","11:25",80)
            fun render(value:BikeNavigationState)=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888).also{BikeNavigationRenderer.draw(Canvas(it),map,layout,value,false)}
            val base=render(state);val alert=render(state.copy(alert=TrafficAlert("sample",kind,500.0)))
            for(y in 0 until height)for(x in 0 until 480)if(x<135 || y>53 || y<3)
                assertEquals("Alert covered existing controls at $x,$y",base.getPixel(x,y),alert.getPixel(x,y))
            File(folder,"${kind.name.lowercase()}-$height.png").outputStream().use{alert.compress(Bitmap.CompressFormat.PNG,100,it)}
            base.recycle();alert.recycle();map.recycle()
        }
    }
    @Test fun tomTomIncidentEndpointAcceptsConfiguredPhoneKey() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("TomTom key is not configured on this phone",TomTomApi.configured(c))
        // Public central-Bangkok area for service access only. No fake GPS or active-route modification.
        val reports=TomTomApi.routeIncidents(c,listOf(IncidentPoint(13.75,100.50),IncidentPoint(13.76,100.51)))
        assertTrue(reports.size<=100)
    }
}
