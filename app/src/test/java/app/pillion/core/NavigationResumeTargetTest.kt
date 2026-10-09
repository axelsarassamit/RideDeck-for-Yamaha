package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class NavigationResumeTargetTest {
    @Test fun stoppedSessionCannotRestoreEvenWithOldSavedCoordinates() {
        assertNull(NavigationResumeTarget.restore(false,"Old route","13.7563","100.5018"))
    }
    @Test fun damagedSavedCoordinatesCannotStartAnUnintendedRoute() {
        for((lat,lon) in listOf(null to "100", "13" to null, "broken" to "100", "NaN" to "100", "13" to "Infinity", "91" to "100", "13" to "181"))
            assertNull(NavigationResumeTarget.restore(true,"Route",lat,lon))
    }
    @Test fun realSavedDestinationAndValidZeroCoordinatesRemainExact() {
        val destination=NavigationResumeTarget.restore(true,"จุดหมาย","13.7563","100.5018")!!
        assertEquals("จุดหมาย",destination.label); assertEquals(13.7563,destination.latitude,0.0); assertEquals(100.5018,destination.longitude,0.0)
        val zero=NavigationResumeTarget.restore(true,"","0","0")!!
        assertEquals("Destination",zero.label); assertEquals(0.0,zero.latitude,0.0); assertEquals(0.0,zero.longitude,0.0)
    }
}
