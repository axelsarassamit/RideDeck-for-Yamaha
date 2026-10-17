package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class CcuAutoConnectTest {
    @Test fun requiresExactMapPeerAndNeverStartsWhileDisabledOrAlreadyActive() {
        val p = CcuAutoConnect()
        assertFalse(p.attempt("bike", true, false))
        p.connected("bike")
        assertFalse(p.attempt("other", true, false))
        assertFalse(p.attempt("bike", false, false))
        assertFalse(p.attempt("bike", true, true))
        assertEquals(0, p.attempts)
        assertTrue(p.attempt("BIKE", true, false))
    }
    @Test fun retriesAreFiniteAndStopIsRespectedUntilNextMapSession() {
        val p = CcuAutoConnect(); p.connected("bike")
        repeat(3) { assertTrue(p.attempt("bike", true, false)) }
        repeat(5) { assertFalse(p.attempt("bike", true, false)) }
        p.connected("bike"); p.pause()
        assertFalse(p.attempt("bike", true, false))
        p.connected("bike"); assertTrue(p.attempt("bike", true, false))
        p.disconnected(); assertFalse(p.attempt("bike", true, false))
    }
}
