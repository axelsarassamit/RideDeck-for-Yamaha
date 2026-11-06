package app.pillion.core

/** Finite read-only startup. Optional replies never block an already established engine stream. */
class CcuReadPlan(private val interval: Int = 1000) {
    init { require(interval in 100..5000) }
    enum class Phase { SERVER, SUPPORTED, LIVE, FAULTS, FAULT_LIVE, READY }
    data class Request(val control: Boolean, val bytes: ByteArray, val response: Int, val label: String, val required: Boolean)
    var phase = Phase.SERVER; private set
    var supported = emptyList<Int>(); private set
    val streamIds get() = supported.intersect(setOf(1, 72)).sorted()
    var unavailable = linkedSetOf<String>(); private set
    fun request(): Request? {
        fun bulk(cmd: Int, response: Int, label: String, ids: List<Int> = emptyList(), rate: Int? = null) =
            Request(false, CcuProtocol.readRequest(cmd, 1248, ids, rate), response, label, false)
        return when (phase) {
            Phase.SERVER -> Request(true, CcuProtocol.control(0x103), 0x104, "Bike capabilities", true)
            Phase.SUPPORTED -> Request(true, CcuProtocol.supportedRequest(1248), 0x127, "Supported vehicle fields", true)
            Phase.LIVE -> bulk(0x110, 0x10f, "Live engine and odometer", streamIds, interval).copy(required = true)
            Phase.FAULTS -> bulk(0x105, 0x106, "Reported diagnostic notices")
            Phase.FAULT_LIVE -> bulk(0x109, 0x106, "Diagnostic notice updates", rate = 1000)
            Phase.READY -> null
        }
    }
    fun accepts(message: CcuProtocol.Message): Boolean {
        val expected = request() ?: return false
        if (message.command != expected.response) return false
        return when (phase) {
            Phase.SERVER -> { require(1248 in CcuProtocol.numberList(message, 3)) { "Observed XMAX server missing" }; true }
            Phase.SUPPORTED -> {
                supported = CcuProtocol.supportedIds(message, 1248).intersect(setOf(1, 72)).sorted()
                require(streamIds.isNotEmpty()) { "Known live vehicle fields unavailable" }; true
            }
            Phase.LIVE -> CcuProtocol.server(message) == 1248 && CcuProtocol.readings(message).any {
                it.id in streamIds && ((it.id == 1 && it.bytes.size == 33) || (it.id == 72 && it.bytes.size == 4))
            }
            Phase.FAULTS -> CcuProtocol.fields(message.payload).any { it.number == 6 && it.integer == 1L }
            Phase.FAULT_LIVE -> CcuProtocol.fields(message.payload).none { it.number == 6 && it.integer == 1L }
            Phase.READY -> false
        }
    }
    fun advance() { if (phase != Phase.READY) phase = Phase.entries[phase.ordinal + 1] }
    fun timeout(): Boolean {
        val request = request() ?: return true
        if (request.required) return false
        unavailable.add(request.label); advance(); return true
    }
}
