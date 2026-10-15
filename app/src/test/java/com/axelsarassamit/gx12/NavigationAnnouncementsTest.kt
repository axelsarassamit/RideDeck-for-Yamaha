package com.axelsarassamit.gx12

import org.junit.Assert.*
import org.junit.Test

class NavigationAnnouncementsTest {
    private fun next(s:NavigationAnnouncements, meters:Double, at:Long=0, speed:Double=20.0, turn:Int=10,
                     arrived:Boolean=false, following:String="") = s.next(turn,-2,meters,"Oak Road",speed,arrived,at,following)
    private fun speak(s:NavigationAnnouncements,meters:Double,at:Long):NavigationAnnouncements.Cue =
        next(s,meters,at)!!.also { s.accepted(it,at) }

    @Test fun advanceApproachAndImmediateAreAllSpokenOnce() {
        val s=NavigationAnnouncements()
        assertTrue(speak(s,1500.0,0).text.startsWith("Navigation started."))
        assertEquals("turn-10-1",speak(s,590.0,30000).id)
        assertEquals("turn-10-2",speak(s,230.0,50000).id)
        assertEquals("Now, turn left onto Oak Road.",speak(s,50.0,60000).text)
        assertNull(next(s,40.0,70000))
    }
    @Test fun lateFixSkipsEarlierStagesAndGpsJitterDoesNotReplayThem() {
        val s=NavigationAnnouncements()
        assertEquals("turn-10-3",speak(s,30.0,0).id)
        for(m in listOf(150.0,500.0,20.0,600.0))assertNull(next(s,m,10000))
    }
    @Test fun fasterRoadsWarnEarlierAndImmediateTurnCanInterruptOlderReminder() {
        val slow=NavigationAnnouncements().apply{ reset(false) }
        val fast=NavigationAnnouncements().apply{ reset(false) }
        assertNull(next(slow,700.0,speed=5.0))
        assertEquals("turn-10-1",next(fast,700.0,speed=30.0)!!.id)
        val s=NavigationAnnouncements()
        speak(s,580.0,0)
        assertNull(next(s,200.0,2000))
        assertEquals("turn-10-3",next(s,40.0,3000)!!.id)
    }
    @Test fun longRoadReminderRequiresTimeAndForwardProgress() {
        val s=NavigationAnnouncements()
        speak(s,5000.0,0)
        assertNull(next(s,5000.0,180000)) // parked
        assertNull(next(s,3800.0,100000)) // too soon
        val cue=next(s,3800.0,180000)!!
        assertTrue(cue.id.startsWith("continue-"));s.accepted(cue,180000)
        assertNull(next(s,3790.0,190000))
    }
    @Test fun rejectedSpeechCanRetryAndNewTurnIsNotPermanentlySuppressed() {
        val s=NavigationAnnouncements()
        val candidate=next(s,150.0)!!
        assertEquals(candidate,next(s,150.0)) // no successful TTS submission yet
        s.accepted(candidate,0)
        assertNotNull(next(s,100.0,7000,turn=20))
    }
    @Test fun afterTurningTheNextLongLegGetsAnInstruction() {
        val s=NavigationAnnouncements()
        speak(s,40.0,0)
        assertNull(next(s,5000.0,2000,turn=20))
        val cue=next(s,5000.0,8000,turn=20)!!
        assertEquals("leg-20",cue.id);s.accepted(cue,8000)
        assertNull(next(s,4990.0,15000,turn=20))
        assertTrue(next(s,3800.0,190000,turn=20)!!.id.startsWith("continue-"))
    }
    @Test fun arrivalAndStatusAreOncePerEpisodeAndStopResetsThem() {
        val s=NavigationAnnouncements()
        s.accepted(s.offRoute()!!,0);assertNull(s.offRoute())
        s.accepted(s.gpsLost()!!,1000);assertNull(s.gpsLost())
        s.onTrack();assertNotNull(s.offRoute());assertNotNull(s.gpsLost())
        val arrival=next(s,0.0,2000,arrived=true)!!
        assertEquals("You have arrived at your destination.",arrival.text)
        s.accepted(arrival,2000)
        assertNull(next(s,0.0,10000,arrived=true));assertNull(s.gpsLost());assertNull(s.offRoute())
        s.reset();assertNotNull(next(s,500.0,20000))
    }
    @Test fun routeRefreshDoesNotRepeatStartAndRecalculationIsClearlyNamed() {
        val s=NavigationAnnouncements()
        s.reset(false);assertNull(next(s,2000.0))
        s.reset(recalculated=true)
        assertTrue(next(s,2000.0)!!.text.startsWith("Route recalculated."))
    }
    @Test fun closeTurnsIncludeNextDirectionAndInvalidDistancesStaySilent() {
        val s=NavigationAnnouncements()
        assertTrue(next(s,100.0,following=NavigationSpeech.following(2,"Pine Road"))!!.text.endsWith("Then, turn right onto Pine Road."))
        assertNull(next(s,Double.NaN));assertNull(next(s,-1.0))
        assertFalse(NavigationSpeech.now(-2,"ถนนสุขุมวิท").any{Character.UnicodeScript.of(it.code)==Character.UnicodeScript.THAI})
    }
}
