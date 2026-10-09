package app.pillion.core

import app.pillion.protocol.NaviLiteCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DashTurn(val sign: Int, val meters: Double, val instruction: String, val road: String = "")
data class DashRoute(val revision: Int, val turns: List<DashTurn>, val activeIndex: Int, val nextMeters: Double)

/** Original interoperability encoder. Layouts checked against StreetCross 1.87 native packers. */
object NativeDashNavigation {
    fun icon(sign: Int): Int = when(sign) {
        -3 -> 32; -2 -> 34; -1 -> 6; 1 -> 7; 2 -> 35; 3 -> 33
        5 -> 0; 6 -> 14; 7 -> 7; else -> 8
    }
    private fun utf8(text: String, maximum: Int): ByteArray {
        var end = text.length.coerceAtMost(maximum)
        if (end > 0 && text[end-1].isHighSurrogate()) end--
        while (end > 0 && text.substring(0,end).toByteArray(Charsets.UTF_8).size > maximum) {
            end--
            if (end > 0 && text[end-1].isHighSurrogate()) end--
        }
        return text.substring(0,end).toByteArray(Charsets.UTF_8)
    }
    private fun distance(meters: Double): Pair<Float, ByteArray> {
        val safe = if(meters.isFinite()) meters.coerceAtLeast(0.0) else 0.0
        return if(safe >= 1000) (safe / 1000).toFloat() to byteArrayOf(107,109)
        else safe.toFloat() to byteArrayOf(109)
    }
    fun nextTurn(sign: Int, meters: Double, road: String): ByteArray {
        val (value,unit) = distance(meters)
        val name = utf8(road,144)
        val payload = ByteBuffer.allocate(7+unit.size+name.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(icon(sign).toByte()).putFloat(value).put(unit.size.toByte()).put(name.size.toByte())
            .put(unit).put(name).array()
        return NaviLiteCodec.build(6,4,1,payload)
    }
    fun listSize(count: Int): ByteArray {
        require(count in 0..65535)
        // StreetCross's native GetTurnByTurnUpdateMessage uses POINTER, not VALUE.
        return NaviLiteCodec.build(6,5,1,byteArrayOf(count.toByte(),(count ushr 8).toByte(),0))
    }
    fun listItem(index: Int, turn: DashTurn): ByteArray {
        require(index in 0..65535)
        val (value,unit) = distance(turn.meters)
        val text = utf8(turn.instruction,112)
        val payload = ByteBuffer.allocate(9+unit.size+text.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(index.toShort()).put(icon(turn.sign).toByte()).put(text.size.toByte())
            .put(unit.size.toByte()).putFloat(value).put(unit).put(text).array()
        return NaviLiteCodec.build(6,97,1,payload)
    }
    fun activeIndex(index: Int): ByteArray {
        require(index in 0..65535)
        return NaviLiteCodec.build(6,6,0,byteArrayOf(index.toByte(),(index ushr 8).toByte()))
    }
    fun imageStopped(): ByteArray = NaviLiteCodec.build(6,20,0,byteArrayOf())
}
