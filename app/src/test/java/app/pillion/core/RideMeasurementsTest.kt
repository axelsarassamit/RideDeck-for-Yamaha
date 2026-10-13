package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class RideMeasurementsTest {
    private fun fix(n:Long=4_000_000_000L,speed:Double=15.0,accuracy:Double=5.0,velocityAccuracy:Double=.5)=RideFix(n,speed,accuracy,velocityAccuracy)
    @Test fun rejectsStaleFutureNoisyOrInvalidSpeed() {
        assertTrue(RideMeasurements.trusted(fix(),4_000_000_000L))
        assertFalse(RideMeasurements.trusted(fix(),8_000_000_000L))
        assertFalse(RideMeasurements.trusted(fix(),3_000_000_000L))
        assertFalse(RideMeasurements.trusted(fix(accuracy=30.0),4_000_000_000L))
        assertFalse(RideMeasurements.trusted(fix(velocityAccuracy=Double.NaN),4_000_000_000L))
        assertFalse(RideMeasurements.trusted(fix(speed=100.0),4_000_000_000L))
    }
    @Test fun accelerationRequiresContiguousAccurateSamples() {
        assertEquals(3.0,RideMeasurements.acceleration(fix(),fix(5_000_000_000L,18.0))!!,.0001)
        assertEquals(-3.0,RideMeasurements.acceleration(fix(),fix(5_000_000_000L,12.0))!!,.0001)
        assertNull(RideMeasurements.acceleration(null,fix()))
        assertNull(RideMeasurements.acceleration(fix(),fix(10_000_000_000L,40.0)))
        assertNull(RideMeasurements.acceleration(fix(),fix(5_000_000_000L,15.5)))
        assertNull(RideMeasurements.acceleration(fix(),fix(5_000_000_000L,50.0)))
        assertFalse(RideMeasurements.plausible(fix(),fix(5_000_000_000L,50.0)))
        assertTrue(RideMeasurements.plausible(fix(),fix(5_000_000_000L,18.0)))
    }
    @Test fun uprightAndRolledFixedMount() {
        val up=doubleArrayOf(0.0,1.0,0.0)
        assertEquals(0.0,RideMeasurements.lean(up,up)!!,.001)
        assertEquals(30.0,kotlin.math.abs(RideMeasurements.lean(doubleArrayOf(.5,kotlin.math.sqrt(.75),0.0),up)!!),.001)
        assertNull(RideMeasurements.lean(doubleArrayOf(0.0,0.0,1.0),doubleArrayOf(0.0,0.0,1.0)))
        assertNull(RideMeasurements.lean(up,doubleArrayOf()))
        assertNull(RideMeasurements.lean(doubleArrayOf(Double.NaN,1.0,0.0),up))
    }
}
