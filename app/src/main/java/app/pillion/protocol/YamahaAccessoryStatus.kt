package app.pillion.protocol

/**
 * Small Yamaha BLE control messages, independently implemented from verified
 * Y-Connect 3.9.0 wire layouts. These are NOT NaviLite frames.
 *
 * No runtime transport calls this codec yet. Accessory discovery/authentication
 * and exact CCU compatibility must be verified before enabling status writes.
 * See docs/YAMAHA_STATUS_ICONS.md for the evidence and remaining work.
 */
object YamahaAccessoryStatus {
    const val SERVICE_UUID = "afa2cdf4-eccf-46a7-a5ea-9da428c0157a"
    const val CONTROL_WRITE_UUID = "b606c7f9-e5a1-4e75-b313-2a920054a8eb"
    const val CONTROL_NOTIFY_UUID = "9c810d26-b605-4306-8c1c-755a1ba3066c"

    // Explicit wire values; Android signal levels and enum ordinals differ.
    enum class CellSignal(val wireValue: Int) {
        NO_CONNECTION(0), NO_SIGNAL(1), LEVEL_1(2), LEVEL_2(3), LEVEL_3(4), LEVEL_4(5)
    }

    /** Unknown or invalid phone values must never become a false battery icon. */
    fun battery(percent: Int?, charging: Boolean?): ByteArray? {
        if (percent == null || percent !in 0..100 || charging == null) return null
        return byteArrayOf(0x01, 0x15, percent.toByte(), if (charging) 1 else 0)
    }

    /** Unknown is omitted, not reinterpreted as airplane mode or no service. */
    fun signal(level: CellSignal?): ByteArray? = level?.let {
        byteArrayOf(0x01, 0x33, it.wireValue.toByte())
    }

    fun headset(connected: Boolean?): ByteArray? = connected?.let {
        byteArrayOf(0x01, 0x1c, if (it) 1 else 0)
    }
}
