package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test

class RouteIncidentsTest {
    private fun point(x:Double,y:Double=0.0)=IncidentPoint(y/111320,x/111320)
    private val distances=doubleArrayOf(0.0,100.0,300.0,600.0,1000.0,3000.0)
    private val route=distances.map{point(it)}
    private fun incident(id:String,geometry:List<IncidentPoint>,line:Boolean=false)=ReportedIncident(id,IncidentKind.ACCIDENT,geometry,line)
    @Test fun accidentOnUpcomingCorridorMatchesButBehindAndOtherRoadDoNot(){
        val reports=listOf(incident("ahead",listOf(point(600.0,5.0))),incident("behind",listOf(point(100.0))),incident("other-road",listOf(point(600.0,40.0))))
        val matches=RouteIncidentMatcher.match(route,distances,300.0,reports)
        assertEquals(listOf("ahead"),matches.map{it.incident.id});assertEquals(600.0,matches.single().startMeters,.1)
    }
    @Test fun lineCrossingAnotherStreetDoesNotBecomeRouteClosure(){
        val crossing=incident("crossing",listOf(point(600.0,-300.0),point(600.0,300.0)),true)
        assertTrue(RouteIncidentMatcher.match(route,distances,0.0,listOf(crossing)).isEmpty())
        val along=incident("along",listOf(point(300.0,2.0),point(2000.0,2.0)),true)
        val match=RouteIncidentMatcher.match(route,distances,0.0,listOf(along)).single()
        assertEquals(300.0,match.startMeters,.1);assertEquals(2000.0,match.endMeters,.1)
    }
    @Test fun invalidGeometryAndUnknownCategoriesAreRejected(){
        assertFalse(IncidentPoint(Double.NaN,1.0).valid());assertNull(IncidentKind.fromCode(6))
        assertEquals(IncidentKind.ROAD_CLOSED,IncidentKind.fromCode(8))
        assertTrue(RouteIncidentMatcher.match(route,distances,0.0,listOf(incident("bad",listOf(IncidentPoint(95.0,0.0))))).isEmpty())
    }
    @Test fun eachIncidentGetsOneAdvanceAndOneApproachWithCooldown(){
        val s=IncidentAnnouncements();val a=TrafficAlert("a",IncidentKind.ACCIDENT,1800.0)
        assertTrue(s.next(a,0));s.accepted(a,0);assertFalse(s.next(a,100000))
        val close=a.copy(meters=300.0);assertFalse(s.next(close,10000));assertTrue(s.next(close,30000))
        s.accepted(close,30000);assertFalse(s.next(a.copy(meters=10.0),100000))
        assertFalse(s.next(a.copy(id="far",meters=5000.0),100000))
        s.reset();assertTrue(s.next(close,100000))
    }
    @Test fun wordingDoesNotClaimConfirmedRoadOrLaneBlockage(){
        for(kind in IncidentKind.entries){val a=TrafficAlert("x",kind,600.0);assertTrue(a.speech.contains("near your route"));assertTrue(a.speech.startsWith("TomTom reports"))}
    }
}
