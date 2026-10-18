package app.pillion.protocol

data class YamahaPhoneStatus(val batteryPercent: Int? = null, val charging: Boolean? = null,
                             val signal: YamahaAccessoryStatus.CellSignal? = null, val headset: Boolean? = null,
                             val phoneVolume: Int? = null, val mediaVolume: Int? = null, val thermal: Int? = null) {
    enum class Network { IN_SERVICE, OUT_OF_SERVICE, EMERGENCY_ONLY, POWER_OFF, UNKNOWN }
    companion object {
        fun percent(level: Int, scale: Int): Int? =
            if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null

        fun cellSignal(simAbsent: Boolean, network: Network, level: Int?): YamahaAccessoryStatus.CellSignal? {
            if (simAbsent || network == Network.POWER_OFF) return YamahaAccessoryStatus.CellSignal.NO_CONNECTION
            if (network == Network.UNKNOWN) return null
            if (network != Network.IN_SERVICE) return YamahaAccessoryStatus.CellSignal.NO_SIGNAL
            return when (level) {
                0 -> YamahaAccessoryStatus.CellSignal.NO_SIGNAL
                1 -> YamahaAccessoryStatus.CellSignal.LEVEL_1
                2 -> YamahaAccessoryStatus.CellSignal.LEVEL_2
                3 -> YamahaAccessoryStatus.CellSignal.LEVEL_3
                4 -> YamahaAccessoryStatus.CellSignal.LEVEL_4
                else -> null
            }
        }

        /** The bike's own hands-free connection must not masquerade as a headset. */
        fun headset(bikeAddress: String, profiles: List<List<String>?>, bleAudio: Boolean?): Boolean? {
            if (bleAudio == true || profiles.any { devices -> devices?.any { !it.equals(bikeAddress, true) } == true }) return true
            if (bleAudio == null || profiles.any { it == null }) return null
            return false
        }
    }
}

/** A session-local change filter. Only an authenticated owner starts publication. */
class YamahaStatusUpdates {
    enum class Kind { BATTERY, SIGNAL, HEADSET, COMBINED }
    data class Update(val kind: Kind, val bytes: ByteArray)
    private var active = false
    private val previous = mutableMapOf<Kind, List<Byte>>()
    fun start() { previous.clear(); active = true }
    fun stop() { active = false; previous.clear() }
    fun updates(status: YamahaPhoneStatus, force: Set<Kind> = emptySet()): List<Update> {
        if (!active) return emptyList()
        val packets = listOf(
            Kind.BATTERY to YamahaAccessoryStatus.battery(status.batteryPercent, status.charging),
            Kind.SIGNAL to YamahaAccessoryStatus.signal(status.signal),
            Kind.HEADSET to YamahaAccessoryStatus.headset(status.headset),
            Kind.COMBINED to YamahaAccessoryStatus.combined(status))
        return packets.mapNotNull { (kind, bytes) ->
            if (bytes == null) { previous.remove(kind); return@mapNotNull null }
            val value = bytes.toList()
            if (kind !in force && previous[kind] == value) return@mapNotNull null
            previous[kind] = value
            Update(kind, bytes)
        }
    }
}
