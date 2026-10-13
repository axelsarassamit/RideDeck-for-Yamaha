package app.pillion.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class CcuProtocolTest {
    private fun bytes(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun rejects(action: () -> Unit) { try { action(); fail("Malformed data accepted") } catch (_: IllegalArgumentException) { } }
    @Test fun classicHandlesShortReadsAndRejectsTruncatedOrHugeFrames() {
        val frame = bytes("0100000002013904")
        val short = object : InputStream() {
            val source = ByteArrayInputStream(frame)
            override fun read() = source.read()
            override fun read(b: ByteArray, off: Int, len: Int) = source.read(b, off, minOf(1, len))
        }
        assertArrayEquals(bytes("0139"), CcuProtocol.readClassic(short))
        rejects { CcuProtocol.readClassic(ByteArrayInputStream(bytes("017fffffff"))) }
        rejects { CcuProtocol.readClassic(ByteArrayInputStream(bytes("0100000002013900"))) }
        try { CcuProtocol.readClassic(ByteArrayInputStream(frame.copyOf(6))); fail() } catch (_: java.io.EOFException) { }
    }
    @Test fun credentialFormatAndAuthAreStrictAndDoNotUseRealKeys() {
        val fake = ByteArray(14) { 65 } + "123456".toByteArray()
        assertArrayEquals(fake, CcuProtocol.credentials(bytes("013a") + fake))
        assertArrayEquals(bytes("0101") + fake, CcuProtocol.authentication(fake))
        rejects { CcuProtocol.credentials(bytes("013a") + ByteArray(20)) }
        rejects { CcuProtocol.authentication(ByteArray(19)) }
    }
    @Test fun envelopeUsesThreeByteLengthAndRejectsMismatch() {
        val body = ByteArray(65530) { 3 }
        assertArrayEquals(body, CcuProtocol.message(CcuProtocol.envelope(0x10f, body)).payload)
        rejects { CcuProtocol.message(bytes("010f010000")) }
        rejects { CcuProtocol.message(bytes("010f000002ff")) }
    }
    @Test fun duplicateFragmentDoesNotFinishEarlyAndMissingConflictingOrExpiredDataFails() {
        val a = CcuProtocol.Assembly(); a.begin(bytes("01000006"), 100)
        val first = bytes("01000000aabbcc")
        assertNull(a.accept(first, 101)); assertNull(a.accept(first, 102))
        assertArrayEquals(bytes("aabbccddeeff"), a.accept(bytes("01000003ddeeff"), 103))
        a.begin(bytes("01000006"), 100)
        rejects { a.accept(bytes("01000003ddeeff"), 101) }
        a.begin(bytes("01000006"), 100); a.accept(first, 101)
        rejects { a.accept(bytes("01000000aabbaa"), 102) }
        rejects { a.accept(bytes("01000003ddeeff"), 15101) }
        rejects { a.begin(bytes("017fffff"), 0) }
    }
    @Test fun protobufSkipsUnknownFieldsAndRawBikeItemsHaveNoInventedUnits() {
        val m = CcuProtocol.message(bytes("010f00000e18e0092209084812030102032801"))
        val reading = CcuProtocol.readings(m).single()
        assertEquals(72, reading.id); assertArrayEquals(bytes("010203"), reading.bytes)
        rejects { CcuProtocol.fields(bytes("22ff")) }
        rejects { CcuProtocol.fields(bytes("00")) }
        rejects { CcuProtocol.fields(bytes("088080808080808080808002")) }
    }
    @Test fun supportedIdsRequireTheCorrectServerAndAcceptPackedAndUnpackedLists() {
        val packed = CcuProtocol.Message(0x127, bytes("18e009220401030948"))
        assertEquals(listOf(1, 3, 9, 72), CcuProtocol.supportedIds(packed, 1248))
        val unpacked = CcuProtocol.Message(0x127, bytes("18e0092001200320092048"))
        assertEquals(listOf(1, 3, 9, 72), CcuProtocol.supportedIds(unpacked, 1248))
        rejects { CcuProtocol.supportedIds(packed, 1249) }
        rejects { CcuProtocol.supportedIds(CcuProtocol.Message(0x127, bytes("18e00922028080")), 1248) }
    }
}
