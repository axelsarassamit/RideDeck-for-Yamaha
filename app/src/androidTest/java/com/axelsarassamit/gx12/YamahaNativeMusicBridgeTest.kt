package com.axelsarassamit.gx12

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Preview only, silent synthetic media session; no bike or real player's playback is touched. */
class YamahaNativeMusicBridgeTest {
    @Test fun optionalIntegrationRetriesBusyTransferAndRoutesOnlyKnownCommands() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        val prefs=RidePreferences.prefs(c)
        val original=prefs.getString("music_app",null)
        val old=prefs.getBoolean("yamaha_native_music_test",false)
        val delivered=CountDownLatch(1);val paused=CountDownLatch(1)
        var session: MediaSession?=null;var bridge: YamahaNativeMusicBridge?=null
        var attempts=0
        try {
            prefs.edit().putString("music_app",c.packageName).putBoolean("yamaha_native_music_test",true).commit()
            instrumentation.runOnMainSync {
                session=MediaSession(c,"Yamaha silent test").apply {
                    setCallback(object: MediaSession.Callback() { override fun onPause() { paused.countDown() } })
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Silent test")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION,120000).build())
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING,1000,1f).build())
                    isActive=true
                }
                bridge=YamahaNativeMusicBridge(c) { frame ->
                    attempts++
                    if(attempts==1) false else {
                        assertEquals(278,app.pillion.core.CcuProtocol.message(frame).command)
                        delivered.countDown();true
                    }
                }.also { it.start() }
            }
            assertTrue("Metadata should retry after a busy bulk transfer",delivered.await(12,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertFalse(bridge!!.control(byteArrayOf(1,0x17,4,0)))
                assertTrue(bridge!!.control(byteArrayOf(1,0x17,4)))
            }
            assertTrue("Known pause should reach only selected session",paused.await(3,TimeUnit.SECONDS))
            instrumentation.runOnMainSync { bridge!!.close() }
        } finally {
            instrumentation.runOnMainSync { bridge?.close();session?.release() }
            prefs.edit().apply { if(original==null)remove("music_app") else putString("music_app",original) }
                .putBoolean("yamaha_native_music_test",old).commit()
        }
    }
}
