package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class SharedMapLinkTest {
    @Test fun sharedPlaceUsesPinRatherThanCamera() {
        assertEquals("13.746,100.534", SharedMapLink.destination("https://www.google.com/maps/place/Store/@1,2,3z/data=!3d13.746!4d100.534"))
        assertNull(SharedMapLink.destination("https://www.google.com/maps/@13.7,100.5,15z"))
    }
    @Test fun directionsUseDestinationAndDecodeAddresses() {
        assertEquals("Central World Bangkok",SharedMapLink.destination("https://www.google.com/maps/dir/?api=1&origin=London&destination=Central+World+Bangkok"))
        assertEquals("13.7,100.5",SharedMapLink.destination("https://www.google.co.th/maps/dir/13.8,100.4/13.7,100.5/@0,0,5z"))
        assertEquals("Coffee Shop",SharedMapLink.destination("https://maps.google.com/?q=Coffee%20Shop"))
    }
    @Test fun permitsShortLinksButRejectsUntrustedRedirects() {
        assertTrue(SharedMapLink.allowed("https://maps.app.goo.gl/Example"))
        assertTrue(SharedMapLink.allowed("https://goo.gl/maps/Example"))
        for (url in listOf("http://maps.google.com/?q=x", "https://google.com.evil.test/maps/place/x", "https://google.com@evil.test/maps", "https://www.google.com:8080/maps", "https://goo.gl/other")) assertFalse(SharedMapLink.allowed(url))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidSharedCoordinates() {
        SharedMapLink.destination("https://www.google.com/maps/search/?api=1&query=91,181")
    }
}
