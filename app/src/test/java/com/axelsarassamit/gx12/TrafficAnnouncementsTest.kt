package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test

class TrafficAnnouncementsTest {
    @Test fun noWarningWithoutRecentMeaningfulReportedDelay() {
        val s=TrafficAnnouncements()
        assertNull(s.next(0))
        for(delay in listOf(-1L,0L,90000L,Long.MAX_VALUE)){s.report(delay,0);assertNull(s.next(0))}
        s.report(180000,0)
        assertEquals("TomTom reports about 3 minutes of traffic delay on your route.",s.next(0)!!.text)
        assertNull(s.next(600001))
    }
    @Test fun stableTrafficDoesNotRepeatButMeaningfulIncreaseDoes() {
        val s=TrafficAnnouncements()
        s.report(180000,0);s.accepted(s.next(0)!!,0)
        s.report(180000,300000);assertNull(s.next(300000))
        s.report(240000,300000);assertNull(s.next(300000))
        s.report(300000,300000);assertEquals(5,s.next(300000)!!.minutes)
    }
    @Test fun improvementResetsComparisonAndWarningsHaveCooldown() {
        val s=TrafficAnnouncements()
        s.report(300000,0);s.accepted(s.next(0)!!,0)
        s.report(0,30000);s.report(180000,60000)
        assertNull(s.next(60000));assertEquals(3,s.next(180000)!!.minutes)
        s.reset();assertNull(s.next(300000))
    }
}
