package com.axelsarassamit.gx12

import org.junit.Assert.*
import org.junit.Test
import app.pillion.core.NativeDashNavigation

class NavigationManeuverTest {
    @Test fun providerUturnsKeepTheirDirectionAcrossDisplays() {
        for (type in listOf("MAKE_UTURN", "TRY_MAKE_UTURN", "MAKE_U_TURN", "make_u_turn", "u-turn", "UTURN_LEFT", "make u turn left")) {
            assertEquals(type, -8, NavigationManeuver.tomTom(type))
        }
        assertEquals(8,NavigationManeuver.tomTom("U_TURN_RIGHT"))
        assertEquals(8,NavigationManeuver.tomTom("make_u_turn", "Right"))
        assertEquals(8,NavigationManeuver.valhalla(12))
        assertEquals(-8,NavigationManeuver.valhalla(13))
        assertEquals(36,NativeDashNavigation.icon(-8))
        assertEquals(37,NativeDashNavigation.icon(8))
        assertNotEquals(NavigationManeuver.glyph(-2),NavigationManeuver.glyph(-8))
        assertNotEquals(NavigationManeuver.glyph(2),NavigationManeuver.glyph(8))
        assertEquals(-2,NavigationManeuver.tomTom("turn_left"))
        assertEquals(0,NavigationManeuver.tomTom("not_a_real_uturn_type"))
    }
    @Test fun genericUturnUsesTrafficSideButExplicitDirectionWins() {
        for (type in listOf("MAKE_UTURN", "TRY_MAKE_UTURN", "MAKE_U_TURN")) {
            assertEquals(8, NavigationManeuver.tomTom(type, drivingSide = "LEFT"))
            assertEquals(-8, NavigationManeuver.tomTom(type, drivingSide = "RIGHT"))
        }
        assertEquals(-8, NavigationManeuver.tomTom("UTURN_LEFT", drivingSide = "LEFT"))
        assertEquals(8, NavigationManeuver.tomTom("UTURN_RIGHT", drivingSide = "RIGHT"))
        assertEquals(-8, NavigationManeuver.tomTom("MAKE_UTURN", "LEFT", "LEFT"))
        assertEquals(8, NavigationManeuver.tomTom("MAKE_UTURN", "RIGHT", "RIGHT"))
        assertEquals(-2, NavigationManeuver.tomTom("TURN_LEFT", drivingSide = "LEFT"))
    }
    @Test fun uturnHasAdvanceAndImmediateSpeechRatherThanOrdinaryTurn() {
        val cues=NavigationAnnouncements()
        val advance=cues.next(3,-8,250.0,"Main Road",10.0,false,0)!!
        assertTrue(advance.text.contains("make a U-turn"))
        cues.accepted(advance,0)
        val immediate=cues.next(3,-8,15.0,"Main Road",10.0,false,1000)!!
        assertTrue(immediate.text.startsWith("Now, make a U-turn"))
        assertTrue(NavigationSpeech.prompt(8,100.0,"",false).contains("make a U-turn"))
        assertFalse(NavigationSpeech.prompt(-2,100.0,"",false).contains("U-turn"))
    }
}
