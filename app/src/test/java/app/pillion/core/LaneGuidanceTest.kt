package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class LaneGuidanceTest {
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
