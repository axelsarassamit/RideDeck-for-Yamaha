package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class RouteTimeEstimatorTest {
    @Test fun usesSlowerAndFasterRoadSectionsInsteadOfDistanceAlone() {
        val legs=listOf(RouteTimeLeg(0.0,1000.0,600000),RouteTimeLeg(1000.0,2000.0,120000))
        assertEquals(720000,RouteTimeEstimator.remainingMillis(2000.0,720000,0.0,legs))
        assertEquals(420000,RouteTimeEstimator.remainingMillis(2000.0,720000,500.0,legs))
        assertEquals(120000,RouteTimeEstimator.remainingMillis(2000.0,720000,1000.0,legs))
        assertEquals(60000,RouteTimeEstimator.remainingMillis(2000.0,720000,1500.0,legs))
    }
    @Test fun reconcilesDetailedTimesWithProviderTotal() {
        val legs=listOf(RouteTimeLeg(0.0,500.0,100000),RouteTimeLeg(500.0,1000.0,100000))
        assertEquals(150000,RouteTimeEstimator.remainingMillis(1000.0,300000,500.0,legs))
    }
    @Test fun missingOrOverlappingTimingFallsBackToWholeRouteEstimate() {
        for(legs in listOf(emptyList(),listOf(RouteTimeLeg(0.0,400.0,300000)),
            listOf(RouteTimeLeg(0.0,600.0,200000),RouteTimeLeg(400.0,1000.0,100000)),
            listOf(RouteTimeLeg(0.0,500.0,300000),RouteTimeLeg(250.0,750.0,200000))))
            assertEquals(225000,RouteTimeEstimator.remainingMillis(1000.0,300000,250.0,legs))
    }
    @Test fun reachingTheTargetClearsRemainingTimeAndBoundsProgress() {
        assertEquals(0,RouteTimeEstimator.remainingMillis(1000.0,300000,1000.0,emptyList()))
        assertEquals(0,RouteTimeEstimator.remainingMillis(1000.0,300000,1200.0,emptyList()))
        assertEquals(300000,RouteTimeEstimator.remainingMillis(1000.0,300000,-50.0,emptyList()))
        assertEquals(300000,RouteTimeEstimator.remainingMillis(1000.0,300000,Double.NaN,emptyList()))
        assertEquals(0,RouteTimeEstimator.remainingMillis(0.0,300000,0.0,emptyList()))
    }
    @Test fun displaysShortMinutesAndLongJourneysWithoutEarlyZero() {
        assertEquals("<1 min",RouteTimeEstimator.durationText(30000))
        assertEquals("2 min",RouteTimeEstimator.durationText(61000))
        assertEquals("1 h 1 min",RouteTimeEstimator.durationText(3600001))
        assertEquals("0 min",RouteTimeEstimator.durationText(0))
    }
    @Test fun arrivalCanCrossMidnightAndCannotOverflow() {
        val now=java.time.Instant.parse("2026-10-09T23:50:00Z").toEpochMilli()
        assertEquals(java.time.Instant.parse("2026-10-10T00:10:00Z").toEpochMilli(),RouteTimeEstimator.arrivalMillis(now,1200000))
        assertEquals(Long.MAX_VALUE,RouteTimeEstimator.arrivalMillis(Long.MAX_VALUE-10,30))
    }
}
