package app.pillion.core

import app.pillion.protocol.NaviLiteCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeDashNavigationTest {
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
