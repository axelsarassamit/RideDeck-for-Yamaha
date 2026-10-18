package com.axelsarassamit.gx12

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.pillion.protocol.YamahaNativeMusic
import java.io.Closeable

/** Optional native-bike integration. Uses only the selected player's existing media session. */
class YamahaNativeMusicBridge(private val context: Context, private val publish: (ByteArray) -> Boolean) : Closeable {
    private val handler=Handler(Looper.getMainLooper())
    private var closed=false
    private var sent: YamahaNativeMusic.Track?=null
    private var sentAt=0L
    private fun enabled()=RidePreferences.prefs(context).getBoolean("yamaha_native_music_test",false)
    private fun controller(): MediaController? = runCatching {
        context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(
            ComponentName(context,GX12NotificationListener::class.java))?.firstOrNull {
                it.packageName == RidePreferences.selectedMusic(context)
            }
    }.getOrNull()
    private val tick=object : Runnable {
        override fun run() {
            if(closed) return
            if(enabled()) {
                val player=controller()
                val metadata=player?.metadata
                val state=player?.playbackState
                val now=SystemClock.elapsedRealtime()
                val position=if(state==null || state.position<0) 0L else {
                    val advance=if(state.state in setOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_REWINDING,
                        PlaybackState.STATE_FAST_FORWARDING) && state.lastPositionUpdateTime>0 && state.playbackSpeed.isFinite())
                        ((now-state.lastPositionUpdateTime).coerceAtLeast(0)*state.playbackSpeed).toLong() else 0L
                    (state.position+advance).coerceAtLeast(0)
                }
                val track=YamahaNativeMusic.Track(
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    ((metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0)/1000).coerceIn(0,0xffffff).toInt(),
                    when(state?.state) {
                        PlaybackState.STATE_PAUSED,PlaybackState.STATE_STOPPED -> 1
                        PlaybackState.STATE_PLAYING -> 2
                        PlaybackState.STATE_REWINDING -> 3
                        PlaybackState.STATE_FAST_FORWARDING -> 4
                        else -> 255
                    },
                    state?.playbackSpeed?.takeIf { it.isFinite() && it in -3276.8f..3276.7f } ?: 0f,
                    (position/1000).coerceIn(0,0xffffff).toInt())
                // Suppress natural progress, but transmit seeks and changed track/state.
                if(YamahaNativeMusic.changed(sent,sentAt,track,now) && publish(YamahaNativeMusic.metadata(track))) {
                    sent=track;sentAt=now
                }
            } else sent=null
            handler.postDelayed(this,1000)
        }
    }
    fun start() { handler.post(tick) }
    fun control(frame: ByteArray): Boolean {
        val action=YamahaNativeMusic.action(frame) ?: return false
        if(closed || !enabled()) return true
        val player=controller() ?: return true
        runCatching {
            val actions=player.playbackState?.actions ?: return@runCatching
            val controls=player.transportControls
            when(action) {
                YamahaNativeMusic.Action.NEXT -> if(actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) controls.skipToNext()
                YamahaNativeMusic.Action.PREVIOUS -> if(actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) controls.skipToPrevious()
                YamahaNativeMusic.Action.PLAY -> if(actions and PlaybackState.ACTION_PLAY != 0L) controls.play()
                YamahaNativeMusic.Action.PAUSE -> if(actions and PlaybackState.ACTION_PAUSE != 0L) controls.pause()
            }
            BikeDiagnostics.record(context,"CCU native music control action=$action")
        }
        return true
    }
    override fun close() { closed=true;handler.removeCallbacksAndMessages(null);sent=null }
}
