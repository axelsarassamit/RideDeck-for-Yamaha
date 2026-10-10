package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class MotorcycleParkingTest {
    private fun record(id:String,lon:Double=0.001,vararg extra:Pair<String,String>)=ParkingRecord(id,0.0,lon,mapOf("amenity" to "motorcycle_parking",*extra))
    @Test fun excludesPrivateProhibitedAndConditionalParkingAndLabelsGeneralFallback() {
        val records=listOf(record("private",extra=arrayOf("access" to "private")),record("no",extra=arrayOf("motorcycle" to "no")),
            record("conditional",extra=arrayOf("access:conditional" to "yes @ (Mo-Fr)")),record("car",extra=arrayOf("amenity" to "parking")),record("valid"))
        val result=MotorcycleParking.suggestions(records,0.0,0.0)
        assertEquals(listOf("valid","car"),result.map { it.record.id });assertTrue(result[0].motorcycleConfirmed);assertFalse(result[1].motorcycleConfirmed)
    }
    @Test fun prefersDedicatedPublicFreeParkingAtSimilarDistance() {
        val records=listOf(record("mixed",extra=arrayOf("amenity" to "parking","motorcycle" to "yes","access" to "yes")),
            record("dedicated",extra=arrayOf("access" to "yes","fee" to "no")),record("far",lon=0.03))
        val result=MotorcycleParking.suggestions(records,0.0,0.0)
        assertEquals(listOf("dedicated","mixed"),result.map { it.record.id });assertTrue(result[0].meters in 110.0..112.0)
    }
    @Test fun acceptsRecordedMotorcycleSpacesAndPreservesUnknownFees() {
        val result=MotorcycleParking.suggestions(listOf(record("spaces",extra=arrayOf("amenity" to "parking","capacity:motorcycle" to "12"))),0.0,0.0)
        assertEquals(1,result.size);assertEquals("",result[0].fee);assertFalse(result[0].dedicated)
    }
}
