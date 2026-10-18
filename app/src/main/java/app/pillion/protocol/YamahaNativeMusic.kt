package app.pillion.protocol

import app.pillion.core.CcuProtocol
import java.io.ByteArrayOutputStream

/** Independently implemented SDK music wire layouts; no audio routing or bike actuation. */
object YamahaNativeMusic {
    enum class Action { NEXT, PREVIOUS, PLAY, PAUSE }
    data class Track(val artist: String, val album: String, val title: String, val duration: Int,
                     val state: Int, val rate: Float, val elapsed: Int)
    fun changed(previous: Track?, previousAt: Long, next: Track, now: Long): Boolean {
        if (previous == null || previous.copy(elapsed=0) != next.copy(elapsed=0)) return true
        val progress = if (previous.state in setOf(2,3,4)) (now-previousAt).coerceAtLeast(0)/1000.0 * previous.rate else 0.0
        return kotlin.math.abs(next.elapsed - (previous.elapsed + progress)) > 2.0
    }
    fun action(frame: ByteArray): Action? {
        if (frame.size != 3 || frame[0] != 1.toByte() || frame[1] != 0x17.toByte()) return null
        return when (frame[2].toInt()) { 1 -> Action.NEXT; 2 -> Action.PREVIOUS; 3 -> Action.PLAY; 4 -> Action.PAUSE; else -> null }
    }
    fun metadata(track: Track): ByteArray {
        require(track.duration in 0..0xffffff && track.elapsed in 0..0xffffff)
        require(track.state in setOf(0,1,2,3,4,255) && track.rate.isFinite() && track.rate in -3276.8f..3276.7f)
        val out=ByteArrayOutputStream()
        fun number(value: Int, size: Int) { repeat(size) { out.write(value ushr ((size-1-it)*8) and 255) } }
        fun text(value: String) {
            val bytes=ByteArrayOutputStream()
            val points=value.codePoints().iterator()
            while(points.hasNext()) {
                val next=String(Character.toChars(points.nextInt())).toByteArray(Charsets.UTF_8)
                if(bytes.size()+next.size>64) break
                bytes.write(next)
            }
            out.write(bytes.size());out.write(bytes.toByteArray())
        }
        text(track.artist);text(track.album);text(track.title)
        number(track.duration,3);number(track.state,1);number((track.rate*10).toInt(),2);number(track.elapsed,3)
        return CcuProtocol.envelope(278,out.toByteArray())
    }
}
