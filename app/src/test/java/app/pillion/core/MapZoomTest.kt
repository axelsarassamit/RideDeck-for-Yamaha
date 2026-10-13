package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class MapZoomTest {
    @Test fun repeatedRequestsAndLimitsAlwaysProduceFeedbackRevision() {
        val zoom=MapZoom()
        var previous=zoom.state().revision
        repeat(30) {
            zoom.change(true)
            assertTrue(zoom.state().revision>previous)
            previous=zoom.state().revision
        }
        assertEquals(MapZoom.MAX_LEVEL,zoom.state().level)
        zoom.change(false)
        assertEquals(MapZoom.MAX_LEVEL-1,zoom.state().level)
        assertTrue(zoom.state().revision>previous)
    }
    @Test fun activeNavigationDoesNotOverrideZoomOut() {
        val zoom=MapZoom(); zoom.navigationStarted()
        assertEquals(16.0,zoom.change(false),0.0)
        zoom.navigationStarted()
        assertEquals(16.0,zoom.level,0.0)
        assertEquals(17.0,zoom.change(true),0.0)
    }
    @Test fun zoomHasUsefulOverviewAndStreetBounds() {
        val zoom=MapZoom()
        repeat(40) { zoom.change(false) }; assertEquals(3.0,zoom.level,0.0)
        repeat(40) { zoom.change(true) }; assertEquals(20.0,zoom.level,0.0)
    }
}
