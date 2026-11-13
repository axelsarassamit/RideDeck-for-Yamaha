package com.axelsarassamit.gx12
import app.pillion.core.*
import org.junit.Assert.*
import org.junit.Test

class LaneManeuverBindingTest {
    private val distances=DoubleArray(31){it*10.0}
    private val turns=listOf(10,22,30)
    private fun lane(a:Int,b:Int)=LaneSection(a,b,listOf(GuidanceLane(listOf(-2),listOf(-2)),GuidanceLane(listOf(0))))
    @Test fun laterJunctionLanesCannotBeLabelledWithNearerTurnDistance() {
        val b=lane(18,22)
        assertNull(LaneManeuverBinding.select(listOf(b),distances,turns,10,0.0,false))
        assertEquals(b,LaneManeuverBinding.select(listOf(b),distances,turns,22,120.0,false))
    }
    @Test fun passedSectionDoesNotBecomeTheFollowingJunctionAndStopLineGapIsBounded() {
        assertNull(LaneManeuverBinding.select(listOf(lane(5,15)),distances,turns,22,160.0,false))
        val short=lane(5,8)
        assertEquals(short,LaneManeuverBinding.select(listOf(short),distances,turns,10,90.0,false))
        assertNull(LaneManeuverBinding.select(listOf(lane(11,11)),distances,turns,22,110.0,false))
    }
    @Test fun valhallaSectionsBelongToTheirExplicitStartingManeuver() {
        val section=lane(10,22)
        assertEquals(section,LaneManeuverBinding.select(listOf(section),distances,turns,10,90.0,true))
        assertNull(LaneManeuverBinding.select(listOf(section),distances,turns,22,150.0,true))
    }
}
