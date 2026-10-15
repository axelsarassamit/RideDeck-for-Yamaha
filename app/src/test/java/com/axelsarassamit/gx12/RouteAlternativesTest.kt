package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test
class RouteAlternativesTest {
    private val straight=listOf(RoutePoint(13.7,100.5),RoutePoint(13.71,100.5))
    private fun option(points:List<RoutePoint> = straight,meters:Double=1112.0,time:Long=60000)=RouteOption(meters,time,points)
    @Test fun denserEncodingOfSameRoadIsRemovedAndFastestIsFirst() {
        val dense=(0..20).map { RoutePoint(13.7+it*0.0005,100.5) }
        assertEquals(listOf(1),RouteAlternatives.indices(listOf(option(time=65000),option(dense,time=60000))))
    }
    @Test fun genuinelyDifferentRoadIsRetainedEvenWithSameEtaAndDistance() {
        val other=listOf(straight[0],RoutePoint(13.705,100.502),straight[1])
        assertEquals(listOf(0,1),RouteAlternatives.indices(listOf(option(),option(other,1200.0))))
    }
    @Test fun loopDetourIsNotHiddenAsDuplicateAndInvalidSummaryIsRejected() {
        val loop=listOf(straight[0],RoutePoint(13.705,100.5),RoutePoint(13.702,100.5),straight[1])
        assertEquals(listOf(0,1),RouteAlternatives.indices(listOf(option(),option(loop,1700.0,90000),option(time=-1))))
    }
}
