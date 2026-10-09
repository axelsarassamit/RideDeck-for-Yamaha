package app.pillion.core

import app.pillion.protocol.NaviLiteCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeDashNavigationTest {
    @Test fun dayNightMatchesInstalledDayOneNightTwoValuePacker() {
        val day=NativeDashNavigation.dayNight(false); val night=NativeDashNavigation.dayNight(true)
        assertEquals(31,NaviLiteCodec.serviceTypeAt(day,0)); assertEquals(6,day[5].toInt()); assertEquals(0,day[11].toInt())
        assertArrayEquals(hex("0100"),NaviLiteCodec.payloadAt(day,0))
        assertArrayEquals(hex("0200"),NaviLiteCodec.payloadAt(night,0))
    }
    @Test fun speedLimitAndClearMatchInstalledFloatPointerPacker() {
        val frame=NativeDashNavigation.speedLimit(50)
        assertEquals(17,NaviLiteCodec.serviceTypeAt(frame,0))
        assertEquals(6,frame[5].toInt()); assertEquals(1,frame[11].toInt())
        assertArrayEquals(hex("00004842046b6d2f68"),NaviLiteCodec.payloadAt(frame,0))
        assertArrayEquals(hex("00000000046b6d2f68"),NaviLiteCodec.payloadAt(NativeDashNavigation.speedLimit(null),0))
    }
    @Test fun unknownSentinelsCannotBecomeNativeSpeedNumbers() {
        for(value in listOf(0,-1,255)) {
            try { NativeDashNavigation.speedLimit(value); fail("Invalid limit accepted") }
            catch(_: IllegalArgumentException) { }
        }
    }
    @Test fun arrivalTimeMatchesInstalledUInt32PointerPacker() {
        val frame=NativeDashNavigation.arrivalTime(23,59)
        assertEquals(1,NaviLiteCodec.serviceTypeAt(frame,0))
        assertEquals(6,frame[5].toInt())
        assertEquals(1,frame[11].toInt())
        assertArrayEquals(hex("9f050000"),NaviLiteCodec.payloadAt(frame,0))
        assertArrayEquals(hex("05000000"),NaviLiteCodec.payloadAt(NativeDashNavigation.arrivalTime(0,5),0))
    }
    @Test fun invalidArrivalClockCannotProduceAPacket() {
        for((hour,minute) in listOf(24 to 0,-1 to 0,0 to 60,0 to -1)) {
            try { NativeDashNavigation.arrivalTime(hour,minute); fail("Invalid time accepted") }
            catch(_: IllegalArgumentException) { }
        }
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun nextTurnMatchesInstalledPackerLayout() {
        val frame = NativeDashNavigation.nextTurn(-2,250.0,"Oak")
        assertEquals(4,NaviLiteCodec.serviceTypeAt(frame,0))
        assertEquals(1,frame[11].toInt())
        assertArrayEquals(hex("2200007a4301036d4f616b"),NaviLiteCodec.payloadAt(frame,0))
    }
    @Test fun turnListRowUsesTextLengthBeforeUnitLengthAndFloat() {
        val frame = NativeDashNavigation.listItem(0x1234,DashTurn(2,150.0,"Turn"))
        assertEquals(97,NaviLiteCodec.serviceTypeAt(frame,0))
        assertArrayEquals(hex("3412230401000016436d5475726e"),NaviLiteCodec.payloadAt(frame,0))
    }
    @Test fun metadataUsesPointerAndActiveIndexUsesValue() {
        val metadata = NativeDashNavigation.listSize(258)
        assertEquals(1,metadata[11].toInt())
        assertArrayEquals(hex("020100"),NaviLiteCodec.payloadAt(metadata,0))
        val index = NativeDashNavigation.activeIndex(258)
        assertEquals(0,index[11].toInt())
        assertArrayEquals(hex("0201"),NaviLiteCodec.payloadAt(index,0))
        assertEquals(0,NaviLiteCodec.payloadAt(NativeDashNavigation.imageStopped(),0).size)
    }
    @Test fun utf8LimitsDoNotSplitEmojiOrThaiCharacters() {
        val frame = NativeDashNavigation.listItem(0,DashTurn(0,1200.0,"😀ก".repeat(100)))
        val payload = NaviLiteCodec.payloadAt(frame,0)
        val textLength = payload[3].toInt() and 255
        val unitLength = payload[4].toInt() and 255
        assertTrue(textLength <= 112)
        val text = payload.copyOfRange(9+unitLength,payload.size)
        assertEquals(textLength,text.size)
        assertFalse(String(text,Charsets.UTF_8).contains('\uFFFD'))
        assertEquals(1.2f,ByteBuffer.wrap(payload,5,4).order(ByteOrder.LITTLE_ENDIAN).float,0.001f)
    }
    @Test fun contentSelectorDistinguishesNativeListFromImages() {
        assertEquals(2,DashContentCommand.contentType(NaviFrameView(55,byteArrayOf(2,0),1,0)))
        assertEquals(1,DashContentCommand.contentType(NaviFrameView(56,byteArrayOf(1,0),1,0)))
        for(payload in listOf(byteArrayOf(),byteArrayOf(2,5),byteArrayOf(11,0),byteArrayOf(2,0,0)))
            assertNull(DashContentCommand.contentType(NaviFrameView(55,payload,1,0)))
        assertNull(DashContentCommand.contentType(NaviFrameView(55,byteArrayOf(2,0),6,0)))
    }
}
