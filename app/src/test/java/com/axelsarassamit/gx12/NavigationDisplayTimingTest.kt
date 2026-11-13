package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test

class NavigationDisplayTimingTest {
    @Test fun idleFramesWaitButEventsAndMovementDoNot() {
        assertFalse(NavigationDisplayTiming.due(1000,1500,0.0,false))
        assertTrue(NavigationDisplayTiming.due(1000,3000,0.0,false))
        assertTrue(NavigationDisplayTiming.due(1000,1500,2.0,false))
        assertTrue(NavigationDisplayTiming.due(1000,1500,null,false))
        assertTrue(NavigationDisplayTiming.due(1000,1001,0.0,true))
        assertTrue(NavigationDisplayTiming.due(0,1,0.0,false))
    }
    @Test fun unreliableHeadingRetainsLastReliableSessionDirection() {
        val heading=NavigationDisplayTiming.Heading()
        assertEquals(0.0,heading.resolve(null,null),0.0)
        assertEquals(92.0,heading.resolve(92.0,null),0.0)
        assertEquals(92.0,heading.resolve(null,null),0.0)
        assertEquals(120.0,heading.resolve(null,120.0),0.0)
        assertEquals(120.0,heading.resolve(null,null),0.0)
        heading.clear()
        assertEquals(0.0,heading.resolve(null,null),0.0)
    }
}
