package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class CcuAdditionalFieldsTest {
    private fun reply(id: Int, server: Int = 1248) = CcuProtocol.Message(0x10f,
        CcuProtocol.integer(3, server.toLong()) +
            byteArrayOf(0x22, 6, 8, id.toByte(), 0x12, 2, 0x41, 0x42))

    @Test fun requestsOnlyAdvertisedIdsIndividuallyAndTimeoutDoesNotBlockNext() {
        val probe = CcuAdditionalFields()
        probe.start(listOf(1, 3, 9, 72, 608))
        assertArrayEquals(CcuProtocol.vehicleRequest(1248, listOf(3)), probe.next())
        assertFalse(probe.accept(reply(1))) // Live engine data cannot satisfy this request.
        assertFalse(probe.accept(reply(3, 1249)))
        probe.timeout()
        assertArrayEquals(CcuProtocol.vehicleRequest(1248, listOf(9)), probe.next())
        assertFalse(probe.accept(reply(3))) // A late reply cannot complete the next field.
        assertTrue(probe.accept(reply(9)))
        assertNull(probe.next())
        assertFalse(probe.active)
        assertEquals(listOf(CcuAdditionalFields.Result.TIMED_OUT, CcuAdditionalFields.Result.REPLIED), probe.observations.map { it.result })
        assertEquals(2, probe.observations.last().bytes)
    }
    @Test fun unsupportedFieldsAreNotSentAndCancellationRetainsNoPayload() {
        val probe = CcuAdditionalFields()
        probe.start(listOf(1, 72))
        assertNull(probe.next())
        assertTrue(probe.observations.all { it.result == CcuAdditionalFields.Result.NOT_ADVERTISED })
        probe.start(listOf(3, 9)); probe.next(); probe.cancel()
        assertFalse(probe.active)
        assertTrue(probe.observations.all { it.result == CcuAdditionalFields.Result.CANCELLED && it.bytes == null })
    }
}
