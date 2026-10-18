package app.pillion.protocol

import app.pillion.core.CcuProtocol
import org.junit.Assert.*
import org.junit.Test

class YamahaNativeMusicTest {
    @Test fun onlyExactKnownControlFramesAreAccepted() {
        for((code,action) in YamahaNativeMusic.Action.entries.withIndex())
            assertEquals(action,YamahaNativeMusic.action(byteArrayOf(1,0x17,(code+1).toByte())))
        for(frame in listOf(byteArrayOf(1,0x17),byteArrayOf(1,0x17,1,0),byteArrayOf(1,0x17,0),
            byteArrayOf(1,0x17,5),byteArrayOf(2,0x17,1),byteArrayOf(1,0x16,1)))
            assertNull(YamahaNativeMusic.action(frame))
    }
    @Test fun metadataUsesBulkEnvelopeAndBigEndianSecondsAndSignedRate() {
        val track=YamahaNativeMusic.Track("A","B","C",300,2,1f,150)
        assertArrayEquals(byteArrayOf(1,0x16,0,0,15,1,65,1,66,1,67,0,1,44,2,0,10,0,0,150.toByte()),
            YamahaNativeMusic.metadata(track))
        val payload=CcuProtocol.message(YamahaNativeMusic.metadata(track.copy(rate=-1f))).payload
        assertEquals(255,payload[10].toInt() and 255)
        assertEquals(246,payload[11].toInt() and 255)
    }
    @Test fun utf8StringsStopBeforeCodePointBoundaryAndInvalidNumbersAreRejected() {
        val track=YamahaNativeMusic.Track("ก".repeat(30),"","",0,255,0f,0)
        val payload=CcuProtocol.message(YamahaNativeMusic.metadata(track)).payload
        assertEquals(63,payload[0].toInt())
        assertEquals("ก".repeat(21),payload.copyOfRange(1,64).toString(Charsets.UTF_8))
        for(invalid in listOf(track.copy(duration=-1),track.copy(elapsed=0x1000000),
            track.copy(rate=Float.NaN),track.copy(state=5))) {
            try { YamahaNativeMusic.metadata(invalid);fail("invalid metadata accepted") } catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun naturalProgressIsSuppressedButSameTrackSeekAndStateChangeArePublished() {
        val track=YamahaNativeMusic.Track("A","B","C",300,2,1f,10)
        assertFalse(YamahaNativeMusic.changed(track,1000,track.copy(elapsed=20),11000))
        assertTrue(YamahaNativeMusic.changed(track,1000,track.copy(elapsed=100),11000))
        assertTrue(YamahaNativeMusic.changed(track,1000,track.copy(state=1,elapsed=20),11000))
        assertFalse(YamahaNativeMusic.changed(track.copy(state=1),1000,track.copy(state=1),11000))
    }

}
