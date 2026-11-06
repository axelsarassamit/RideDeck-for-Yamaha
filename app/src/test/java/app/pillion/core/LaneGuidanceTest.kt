package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class LaneGuidanceTest {
    @Test fun junctionPictureRequiresNearbyMatchingProviderArrows() {
        val lane=GuidanceLane(listOf(2),preferred=listOf(2))
        val right=LaneSection(0,1,List(3){GuidanceLane(listOf(0))}+listOf(lane,lane))
        assertEquals(1,right.junctionSide(2,350.0))
        assertEquals(0,right.junctionSide(-2,350.0))
        assertEquals(0,right.junctionSide(2,451.0))
        assertEquals(0,right.junctionSide(2,null))
        assertEquals(0,right.copy(lanes=right.lanes.reversed()).junctionSide(2,350.0))
    }
    @Test fun fiveLaneRoadIdentifiesExactLaneGroups() {
        fun section(vararg recommended: Int) = LaneSection(0, 1, List(5) { i ->
            GuidanceLane(listOf(0), valid = if(i in recommended) listOf(0) else emptyList())
        })
        assertEquals("Use the two leftmost lanes.", section(0,1).spoken())
        assertEquals("Use the two rightmost lanes.", section(3,4).spoken())
        assertEquals("Use lanes 2 and 4 from the left.", section(1,3).spoken())
        val section = section(0,1,2,3,4)
        val preferred = section.copy(lanes = section.lanes.mapIndexed { i,lane ->
            if(i>=3) lane.copy(preferred=listOf(0)) else lane
        })
        assertEquals(listOf(3,4),preferred.recommendedIndices())
        assertEquals("Use the two rightmost lanes.",preferred.spoken())
    }
    @Test fun recommendedLanesStayLeftToRightAndExpireAfterSection() {
        assertEquals(listOf(-2,0),LaneGuidance.mask(10).sorted())
        assertEquals(-8,LaneGuidance.direction("LEFT_U_TURN"))
        assertNull(LaneGuidance.direction("unknown"))
        val section=LaneSection(2,3,listOf(GuidanceLane(listOf(-2),listOf(-2)),GuidanceLane(listOf(0))))
        val distances=doubleArrayOf(0.0,100.0,200.0,220.0)
        assertEquals("Use the left lane.",section.spoken())
        assertEquals(section,LaneGuidance.select(listOf(section),distances,0.0,300.0))
        assertNull(LaneGuidance.select(listOf(section),distances,221.0,300.0))
        assertNull(LaneGuidance.select(listOf(section.copy(end=8)),distances,0.0,300.0))
    }
    @Test fun absentRecommendationsNeverInventGuidance() {
        val section=LaneSection(1,2,listOf(GuidanceLane(listOf(-2)),GuidanceLane(listOf(0))))
        assertFalse(section.usable());assertEquals("",section.spoken())
        assertNull(LaneGuidance.select(listOf(section),doubleArrayOf(0.0,100.0,200.0),0.0,300.0))
    }
    @Test fun preferencesUseSupportedProviderOptionsOnly() {
        val p=RoadPreferences(avoidFerries=true)
        assertEquals(listOf("motorways","tollRoads","ferries","unpavedRoads"),p.tomTomAvoids())
        assertEquals(0.0,p.valhallaOptions()["use_ferry"])
        assertEquals(true,p.valhallaOptions()["exclude_unpaved"])
        assertTrue(RoadPreferences(false,false,false,false).tomTomAvoids().isEmpty())
    }
}
