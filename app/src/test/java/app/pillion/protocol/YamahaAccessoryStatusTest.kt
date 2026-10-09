package app.pillion.protocol

import org.junit.Assert.*
import org.junit.Test

class YamahaAccessoryStatusTest {
    @Test fun batteryMatchesVerifiedBigEndianCommandAndBytePayload() {
        assertArrayEquals(bytes(0x01, 0x15, 73, 1), YamahaAccessoryStatus.battery(73, true))
        assertArrayEquals(bytes(0x01, 0x15, 0, 0), YamahaAccessoryStatus.battery(0, false))
        assertArrayEquals(bytes(0x01, 0x15, 100, 0), YamahaAccessoryStatus.battery(100, false))
    }

    @Test fun signalMatchesAllSixSdkWireValuesWithoutAndroidOrdinalConfusion() {
        val fixtures = listOf(
            YamahaAccessoryStatus.CellSignal.NO_CONNECTION to bytes(1, 0x33, 0),
            YamahaAccessoryStatus.CellSignal.NO_SIGNAL to bytes(1, 0x33, 1),
            YamahaAccessoryStatus.CellSignal.LEVEL_1 to bytes(1, 0x33, 2),
            YamahaAccessoryStatus.CellSignal.LEVEL_2 to bytes(1, 0x33, 3),
            YamahaAccessoryStatus.CellSignal.LEVEL_3 to bytes(1, 0x33, 4),
            YamahaAccessoryStatus.CellSignal.LEVEL_4 to bytes(1, 0x33, 5)
        )
        for ((signal, packet) in fixtures) assertArrayEquals(packet, YamahaAccessoryStatus.signal(signal))
    }

    @Test fun headsetMatchesVerifiedResponseAndDisconnectClearsState() {
        assertArrayEquals(bytes(1, 0x1c, 1), YamahaAccessoryStatus.headset(true))
        assertArrayEquals(bytes(1, 0x1c, 0), YamahaAccessoryStatus.headset(false))
    }

    @Test fun unavailableAndMalformedStateIsOmitted() {
        for (percent in listOf(null, -1, 101, 255, Int.MAX_VALUE))
            assertNull(YamahaAccessoryStatus.battery(percent, false))
        assertNull(YamahaAccessoryStatus.battery(50, null))
        assertNull(YamahaAccessoryStatus.signal(null))
        assertNull(YamahaAccessoryStatus.headset(null))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
