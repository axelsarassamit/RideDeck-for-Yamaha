package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class DestinationInputTest {
    @Test fun variantsAndPunctuationRemainReadable() {
        assertEquals(listOf("Store Puchao, Sukhumvit Road, Mueang Samut Prakan","Store Puchao"),DestinationInput.queries("Store Pu Chao , Sukhumvit Rd. , Mueng Samut Prakan"))
        assertEquals("ถนนสุขุมวิท",DestinationInput.normalize("  ถนนสุขุมวิท  "))
    }
    @Test fun pastedCoordinatesDoNotNeedGeocoding() {
        for(input in listOf("13.6, 100.5","13.6 100.5","13.6;100.5","loc:13.6,100.5")) assertEquals(13.6 to 100.5,DestinationInput.coordinates(input))
        assertNull(DestinationInput.coordinates("13 Main Street"))
        try { DestinationInput.coordinates("91,100");fail() } catch(_:IllegalArgumentException) {}
    }
}
