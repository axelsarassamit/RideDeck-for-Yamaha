package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class SampledFuelSessionTest {
    private fun bytes(raw: Int) = byteArrayOf(0, 0, (raw shr 8).toByte(), raw.toByte())
    @Test fun repeatedEqualSamplesAreValidAndNotDeduplicated() {
        val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
        repeat(5) { s.sample("bike", it * 1000L, bytes(1000), 100.0 + it * 0.05) }
        assertEquals(0.05, s.result(4000).litres!!, 0.000001)
        assertEquals(4.0, s.result(4000).kmPerLitre!!, 0.000001)
    }
    @Test fun unverifiedDeliveryNeverExposesPhysicalTotal() {
        val s = SampledFuelSession("bike", 100.0, 0)
        repeat(5) { s.sample("bike", it * 1000L, bytes(1000), 100.0) }
        assertNull(s.result(4000).litres); assertNull(s.result(4000).kmPerLitre)
        assertTrue(s.result(4000).reason!!.contains("verification"))
    }
    @Test fun duplicateBurstInvalidatesEntireTotal() {
        val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
        s.sample("bike", 0, bytes(10), 100.0); s.sample("bike", 100, bytes(10), 100.0)
        assertNull(s.result(100).litres); assertNotNull(s.result(100).reason)
    }
    @Test fun gapCannotSilentlyUnderreport() {
        val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
        repeat(5) { s.sample("bike", it * 1000L, bytes(10), 100.0) }
        assertNull(s.result(7000).litres)
        s.sample("bike", 8000, bytes(10), 100.0); assertNull(s.result(8000).litres)
    }
    @Test fun otherBikeOrRollbackInvalidates() {
        for ((bike, odo) in listOf("other" to 100.0, "bike" to 99.9)) {
            val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
            s.sample(bike, 0, bytes(10), odo); assertNotNull(s.result(0).reason)
        }
    }
    @Test fun staleOdometerAndTruncatedSamplesAreRejected() {
        for ((raw, odo) in listOf(bytes(10) to null, byteArrayOf(1, 2) to 100.0)) {
            val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
            s.sample("bike", 0, raw, odo); assertNotNull(s.result(0).reason)
        }
    }
    @Test fun idleZeroFuelHasNoEconomyAndNoFakeConsumption() {
        val s = SampledFuelSession("bike", 100.0, 0, deliveryVerified = true)
        repeat(5) { s.sample("bike", it * 1000L, bytes(0), 100.0) }
        assertEquals(0.0, s.result(4000).litres!!, 0.0); assertNull(s.result(4000).kmPerLitre)
        s.invalidate("Disconnected"); assertNull(s.result(4000).litres)
    }
    @Test fun oneOdometerTickDoesNotFailFromFloatingPointRounding() {
        val s=SampledFuelSession("bike",15166.2,0)
        s.sample("bike",0,bytes(10),15166.2)
        s.sample("bike",1000,bytes(10),15166.3)
        assertEquals(2,s.result(1000).samples)
    }
}
