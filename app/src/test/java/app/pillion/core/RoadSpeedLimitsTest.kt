package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class RoadSpeedLimitsTest {
    @Test fun recordedPublicProviderWindowKeepsThirtyFiftyThirtyBoundaries() {
        val points=ArrayList<SpeedLimitPoint>(); val matched=ArrayList<SpeedLimitPoint>(); val edges=ArrayList<SpeedLimitEdge>()
        javaClass.getResourceAsStream("/public-berlin-speed-window.tsv")!!.bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") && it.isNotBlank() }.forEach { line ->
                val fields=line.split('\t')
                when(fields[0]) {
                    "P" -> points.add(SpeedLimitPoint(fields[1].toDouble(),fields[2].toDouble()))
                    "M" -> matched.add(SpeedLimitPoint(fields[1].toDouble(),fields[2].toDouble()))
                    "E" -> edges.add(SpeedLimitEdge(fields[1].toInt(),fields[2].toInt(),fields.getOrNull(3)?.toDoubleOrNull()))
                }
            }
        }
        assertEquals(108,points.size); assertEquals(109,matched.size)
        val limits=RoadSpeedLimits.align(points,matched,RoadSpeedLimits.segments(matched.size,edges))
        assertEquals(107,limits.size)
        assertEquals(30,limits[0]); assertEquals(30,limits[33]); assertEquals(50,limits[34])
        assertEquals(50,limits[73]); assertEquals(30,limits[74]); assertEquals(30,limits.last())
    }
    @Test fun repeatedBoundaryPointKeepsTheNextRoadLimitOnTheNextSegment() {
        val a=SpeedLimitPoint(1.0,1.0); val b=SpeedLimitPoint(1.01,1.01); val c=SpeedLimitPoint(1.02,1.02)
        assertArrayEquals(intArrayOf(50,30),RoadSpeedLimits.align(listOf(a,b,c),listOf(a,b,b,c),intArrayOf(50,0,30)))
        assertArrayEquals(intArrayOf(0,50,30),RoadSpeedLimits.align(listOf(a,a,b,c),listOf(a,b,b,c),intArrayOf(50,0,30)))
    }
    @Test fun changedRoadGeometryRejectsLimitsInsteadOfReusingIndexes() {
        val a=SpeedLimitPoint(1.0,1.0); val b=SpeedLimitPoint(1.01,1.01); val c=SpeedLimitPoint(1.02,1.02)
        try { RoadSpeedLimits.align(listOf(a,b,c),listOf(a,SpeedLimitPoint(1.015,1.015),c),intArrayOf(50,30)); fail("Changed road accepted") }
        catch(_: IllegalArgumentException) { }
    }
    @Test fun roadBoundariesSwitchLimitsWithoutCarryingIntoUnknownRoads() {
        val window=SpeedLimitWindow(10,RoadSpeedLimits.segments(5,listOf(SpeedLimitEdge(0,1,50.0),SpeedLimitEdge(1,3,30.0))))
        assertNull(window.at(9)); assertEquals(50,window.at(10)); assertEquals(30,window.at(11))
        assertEquals(30,window.at(12)); assertNull(window.at(13)); assertNull(window.at(14))
    }
    @Test fun unknownUnlimitedOrMalformedValuesNeverBecomePostedNumbers() {
        for(value in listOf(null,0.0,255.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,30.5)) assertNull(RoadSpeedLimits.numeric(value))
        assertEquals(30,RoadSpeedLimits.numeric(30.0))
        assertArrayEquals(intArrayOf(0,0,0),RoadSpeedLimits.segments(4,listOf(SpeedLimitEdge(0,1,0.0),SpeedLimitEdge(1,2,255.0),SpeedLimitEdge(2,3,null))))
    }
    @Test fun overlappingEdgesAreAmbiguousAndHidden() {
        assertArrayEquals(intArrayOf(50,0,30),RoadSpeedLimits.segments(4,listOf(SpeedLimitEdge(0,2,50.0),SpeedLimitEdge(1,3,30.0))))
    }
    @Test fun invalidShapeIndexesCannotAttachLimitsToAnotherSegment() {
        assertArrayEquals(intArrayOf(0,0),RoadSpeedLimits.segments(3,listOf(SpeedLimitEdge(-1,1,50.0),SpeedLimitEdge(1,4,30.0),SpeedLimitEdge(2,1,60.0))))
    }
    @Test fun staleOrUncertainPositionAndRecalculationHideTheLimit() {
        assertTrue(RoadSpeedLimits.visible(true,false,10f,3.0,10.0))
        assertFalse(RoadSpeedLimits.visible(false,false,10f,3.0,10.0))
        assertFalse(RoadSpeedLimits.visible(true,true,10f,3.0,10.0))
        assertFalse(RoadSpeedLimits.visible(true,false,21f,3.0,10.0))
        assertFalse(RoadSpeedLimits.visible(true,false,10f,21.0,10.0))
        assertFalse(RoadSpeedLimits.visible(true,false,10f,3.0,61.0))
        assertFalse(RoadSpeedLimits.visible(true,false,Float.NaN,3.0,10.0))
    }
}
