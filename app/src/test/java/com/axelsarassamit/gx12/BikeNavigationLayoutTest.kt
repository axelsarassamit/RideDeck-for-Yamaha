package com.axelsarassamit.gx12
import app.pillion.core.NativeDashNavigation
import app.pillion.protocol.NaviLiteCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BikeNavigationLayoutTest {
    @Test fun bothDisplaysKeepMapAndSignOutOfTextAndCredits() {
        for(height in listOf(234,240)) {
            val l=BikeNavigationLayout(height)
            assertEquals(480,l.panelWidth+l.mapWidth)
            assertEquals(height,l.mapHeight+l.roadHeight+l.creditHeight)
            assertTrue(l.mapWidth >= 348 && l.mapHeight >= 196)
            assertTrue(l.speedX-l.speedRadius > l.mapLeft)
            assertTrue(l.speedX+l.speedRadius < l.panelLeft)
            assertTrue(l.speedY+l.speedRadius < l.mapHeight)
        }
    }
    @Test fun mapToNativeToMapClearsDuplicateWithoutLosingNativeTurnSign() {
        val packets=listOf(true,false,true).map { images ->
            NaviLiteCodec.payloadAt(NativeDashNavigation.speedLimit(BikeNavigationLayout.nativeSpeedLimit(images,80)),0)
        }
        assertEquals(listOf(0f,80f,0f),packets.map { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).float })
        assertNull(BikeNavigationLayout.nativeSpeedLimit(false,null))
    }
}
