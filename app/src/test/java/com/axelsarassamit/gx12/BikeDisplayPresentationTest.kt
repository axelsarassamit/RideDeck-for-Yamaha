package com.axelsarassamit.gx12

import app.pillion.core.GuidanceLane
import app.pillion.core.LaneSection
import org.junit.Assert.*
import org.junit.Test

class BikeDisplayPresentationTest {
    private val lanes=LaneSection(0,1,listOf(GuidanceLane(listOf(-2),listOf(-2)),GuidanceLane(listOf(0))))
    @Test fun junctionRequiresProviderLanesAndFreshUpcomingManeuver() {
        assertTrue(BikeDisplayPresentation.junction(false,lanes,200.0,true))
        assertFalse(BikeDisplayPresentation.junction(false,null,200.0,true))
        assertFalse(BikeDisplayPresentation.junction(false,lanes,200.0,false))
        assertFalse(BikeDisplayPresentation.junction(false,lanes,Double.NaN,true))
        assertFalse(BikeDisplayPresentation.junction(false,lanes,301.0,true))
        assertTrue(BikeDisplayPresentation.junction(true,lanes,340.0,true))
        assertFalse(BikeDisplayPresentation.junction(true,lanes,351.0,true))
    }
    @Test fun etaAndEstimatedRangeAlternateOnlyWhenBothAvailable() {
        assertEquals("Arrival",BikeDisplayPresentation.footer("11:25","Est. fuel range ~180 km",9999).label)
        assertEquals("Est. range",BikeDisplayPresentation.footer("11:25","Est. fuel range ~180 km",10000).label)
        assertEquals("~180 km",BikeDisplayPresentation.footer("11:25","Est. fuel range ~180 km",10000).value)
        assertEquals("Arrival",BikeDisplayPresentation.footer("11:25",null,10000).label)
        assertEquals("Est. range",BikeDisplayPresentation.footer(null,"Est. fuel range ~180 km",0).label)
        assertEquals("—",BikeDisplayPresentation.footer(null,null,0).value)
    }
    @Test fun junctionLeavesAnIndependentMapAndStableRightRailAtBothHeights() {
        for(height in listOf(234,240)) {
            val normal=BikeNavigationLayout(height)
            val junction=BikeNavigationLayout(height,true)
            assertEquals(normal.panelLeft,junction.panelLeft)
            assertEquals(normal.mapHeight,junction.mapHeight)
            assertTrue(junction.mapLeft>=160)
            assertTrue(junction.mapWidth>=176)
            assertEquals(junction.panelLeft,junction.mapLeft+junction.mapWidth)
            assertTrue(junction.speedX-junction.speedRadius>junction.mapLeft)
        }
    }
}
