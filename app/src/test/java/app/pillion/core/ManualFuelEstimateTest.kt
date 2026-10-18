package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class ManualFuelEstimateTest {
    private val a=ManualFuelAnchor("bike",1000.0,13.0,25.0,2.0)
    @Test fun explicitEstimateAndBuffer() {
        val r=ManualFuelEstimate.estimate(a,1100.0,"bike")!!
        assertEquals(9.0,r.remainingLitres,0.0001);assertEquals(175.0,r.refuelKm,0.0001)
    }
    @Test fun partialRefillsAreIncludedInFullToFullLearning() {
        val partial=ManualFuelEstimate.partial(a,1100.0,2.0)!!
        val full=ManualFuelEstimate.nextFull(partial,1200.0,6.0,123L)!!
        assertEquals(25.0,full.kmPerLitre,0.0001);assertEquals(0.0,full.partialLitres,0.0001)
        assertEquals(13.0,ManualFuelEstimate.estimate(full,1200.0,"bike")!!.remainingLitres,0.0001)
    }
    @Test fun unknownOrContradictoryInputsAreRejected() {
        assertNull(ManualFuelEstimate.estimate(a,999.0,"bike"));assertNull(ManualFuelEstimate.estimate(a,1100.0,"other"))
        assertNull(ManualFuelEstimate.estimate(a,9000.0,"bike"));assertNull(ManualFuelEstimate.estimate(a,Double.NaN,"bike"))
        assertNull(ManualFuelEstimate.partial(a,1000.0,1.0));assertNull(ManualFuelEstimate.partial(a,1100.0,-1.0))
        assertNull(ManualFuelEstimate.nextFull(a,1001.0,1.0,0));assertFalse(ManualFuelEstimate.valid(a.copy(kmPerLitre=0.0)))
        assertFalse(ManualFuelEstimate.valid(a.copy(bufferLitres=13.0)))
    }
    @Test fun refillOdometerCannotRollBack() {
        val p=ManualFuelEstimate.partial(a,1100.0,2.0)!!
        assertNull(ManualFuelEstimate.estimate(p,1099.0,"bike"));assertNull(ManualFuelEstimate.nextFull(p,1099.0,1.0,0))
    }
    @Test fun exhaustedEstimateIsNotNegative() {
        val r=ManualFuelEstimate.estimate(a,1400.0,"bike")!!
        assertEquals(0.0,r.refuelKm,0.0);assertEquals(0.0,r.remainingLitres,0.0)
    }
}
