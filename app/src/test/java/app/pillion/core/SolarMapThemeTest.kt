package app.pillion.core

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class SolarMapThemeTest {
    private fun night(time: String,lat: Double=13.7563,lon: Double=100.5018) = SolarMapTheme.darkAt(Instant.parse(time).toEpochMilli(),lat,lon)
    @Test fun bangkokChangesAroundSunriseAndSunsetRatherThanFixedClockHours() {
        // 06:00, 06:30, 17:30 and 18:30 in Thailand.
        assertEquals(true,night("2026-10-08T23:00:00Z"))
        assertEquals(false,night("2026-10-08T23:30:00Z"))
        assertEquals(false,night("2026-10-09T10:30:00Z"))
        assertEquals(true,night("2026-10-09T11:30:00Z"))
    }
    @Test fun longitudeChangesDaylightAndDatelineCoordinatesAgree() {
        val noon="2026-03-20T12:00:00Z"
        assertEquals(false,night(noon,0.0,0.0))
        assertEquals(true,night(noon,0.0,180.0))
        assertEquals(night(noon,0.0,180.0),night(noon,0.0,-180.0))
        assertEquals(false,night("2026-03-20T04:00:00Z",0.0,120.0))
        assertEquals(true,night("2026-03-20T04:00:00Z",0.0,-120.0))
    }
    @Test fun polarDayAndNightWorkInBothHemispheres() {
        for(hour in listOf("00","06","12","18")) {
            assertEquals(false,night("2026-06-21T${hour}:00:00Z",80.0,0.0))
            assertEquals(true,night("2026-12-21T${hour}:00:00Z",80.0,0.0))
            assertEquals(true,night("2026-06-21T${hour}:00:00Z",-80.0,0.0))
            assertEquals(false,night("2026-12-21T${hour}:00:00Z",-80.0,0.0))
        }
    }
    @Test fun leapDayAndUtcMidnightDoNotResetToTheWrongLocalMode() {
        assertEquals(false,night("2024-02-29T23:59:59Z",0.0,120.0))
        assertEquals(false,night("2024-03-01T00:00:01Z",0.0,120.0))
        assertEquals(true,night("2024-02-29T23:59:59Z",0.0,-30.0))
        assertEquals(true,night("2024-03-01T00:00:01Z",0.0,-30.0))
    }
    @Test fun changingPhoneTimeZoneCannotChangeTheSunAtTheSamePlaceAndInstant() {
        val previous=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok")); val thai=night("2026-10-09T05:00:00Z")
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")); val pacific=night("2026-10-09T05:00:00Z")
            assertEquals(false,thai); assertEquals(thai,pacific)
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun invalidCoordinatesReturnUnknownForTheCallerToKeepItsCurrentMode() {
        val now=Instant.parse("2026-10-09T05:00:00Z").toEpochMilli()
        for((lat,lon) in listOf(Double.NaN to 0.0,0.0 to Double.POSITIVE_INFINITY,91.0 to 0.0,0.0 to -181.0)) assertNull(SolarMapTheme.darkAt(now,lat,lon))
    }
}
