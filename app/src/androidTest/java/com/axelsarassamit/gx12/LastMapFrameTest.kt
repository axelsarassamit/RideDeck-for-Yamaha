package com.axelsarassamit.gx12

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastMapFrameTest {
    @Test fun delayedFrameKeepsRoadsWithoutReplayingOldOverlays() {
        val base = Bitmap.createBitmap(360, 190, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val live = Bitmap.createBitmap(480, 234, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val frame = NativeNavigation.MapFrame(live, base, 1000) { byteArrayOf(it.width.toByte()) }

        assertTrue(frame.fresh(10999))
        assertFalse(frame.fresh(11000))
        assertEquals(live.width, frame.lastMap.width)
        assertEquals(live.height, frame.lastMap.height)
        assertEquals(Color.GREEN, frame.lastMap.getPixel(240, 117))
        assertNotEquals(Color.GREEN, frame.lastMap.getPixel(0, 0))
        assertSame(frame.lastJpeg, frame.lastJpeg)

        frame.invalidated = true
        assertFalse(frame.fresh(1001))
    }
}
