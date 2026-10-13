package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class FuelRangeTest {
    private val now=50_000_000_000L
    private val reading=BikeFuelReading(5.0,4.0,13.0,now,"Verified bike adapter")
    @Test fun distanceTimeAndBufferUseVerifiedQuantities() {
        val result=FuelRange.estimate(reading,now,50.0,1.0)!!
        assertEquals(125.0,result.kilometresToEmpty,.0001)
        assertEquals(150.0,result.ridingMinutesToEmpty!!,.0001)
        assertEquals(100.0,result.kilometresToRefuel,.0001)
    }
    @Test fun unknownAndStaleNeverBecomeZeroOrUnlimited() {
        assertNull(FuelRange.estimate(null,now,50.0,1.0))
        assertNull(FuelRange.estimate(reading,now+31_000_000_000L,50.0,1.0))
        assertNull(FuelRange.estimate(reading.copy(litresPer100Km=0.0),now,50.0,1.0))
        assertNull(FuelRange.estimate(reading.copy(remainingLitres=14.0),now,50.0,1.0))
        assertNull(FuelRange.estimate(reading.copy(remainingLitres=Double.NaN),now,50.0,1.0))
    }
    @Test fun stationaryAndUnknownSpeedHideTime() {
        assertNull(FuelRange.estimate(reading,now,0.0,1.0)!!.ridingMinutesToEmpty)
        assertNull(FuelRange.estimate(reading,now,null,1.0)!!.ridingMinutesToEmpty)
        assertEquals(0.0,FuelRange.estimate(reading.copy(remainingLitres=0.0),now,40.0,1.0)!!.kilometresToEmpty,.0001)
    }
}
