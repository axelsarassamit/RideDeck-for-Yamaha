package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class TomTomAlternativesTest {
    @Test fun singleItineraryNeverRequestsAnAlternativeType() {
        val p=TomTomAlternatives.parameters(false)
        assertEquals("0",p["maxAlternatives"])
        assertFalse(p.containsKey("alternativeType"))
    }
    @Test fun alternativesHaveAPositiveCount() {
        val p=TomTomAlternatives.parameters(true)
        assertEquals("2",p["maxAlternatives"])
        assertEquals("anyRoute",p["alternativeType"])
    }
}
