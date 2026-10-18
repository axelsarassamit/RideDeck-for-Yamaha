package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test
class NavigationTurnSelectionTest {
    private val turns=listOf(NavigationTurnSelection.Turn(0.0,0),NavigationTurnSelection.Turn(100.0,-8),NavigationTurnSelection.Turn(500.0,5))
    @Test fun approachExactPointAndSmallOvershootKeepImmediateUturn() {
        for (position in listOf(0.0,85.0,100.0,107.9)) assertEquals(1,NavigationTurnSelection.next(turns,position))
        assertEquals(2,NavigationTurnSelection.next(turns,108.1))
        assertEquals(2,NavigationTurnSelection.next(turns,500.0))
        assertEquals(2,NavigationTurnSelection.next(turns,530.0))
    }
    @Test fun closelySpacedTurnsDoNotHideTheNextManeuver() {
        val close=listOf(NavigationTurnSelection.Turn(100.0,-8),NavigationTurnSelection.Turn(106.0,2),NavigationTurnSelection.Turn(200.0,5))
        assertEquals(0,NavigationTurnSelection.next(close,100.0))
        assertEquals(1,NavigationTurnSelection.next(close,104.0))
        assertEquals(1,NavigationTurnSelection.next(close,106.0))
        assertEquals(-1,NavigationTurnSelection.next(emptyList(),0.0))
    }
}
