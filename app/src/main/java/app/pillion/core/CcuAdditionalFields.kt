package app.pillion.core

/** Explicit parked-only reads of bike-declared fields. Retains lengths, never unknown payloads. */
class CcuAdditionalFields {
    enum class Result { NOT_ADVERTISED, WAITING, REPLIED, TIMED_OUT, CANCELLED }
    data class Observation(val id: Int, val result: Result, val bytes: Int? = null)
    private val results = linkedMapOf<Int, Observation>()
    private val pending = ArrayDeque<Int>()
    var waiting: Int? = null; private set
    val active get() = waiting != null || pending.isNotEmpty()
    val observations get() = results.values.toList()

    fun start(supported: List<Int>) {
        check(!active)
        results.clear()
        for (id in listOf(3, 9)) {
            if (id in supported) pending.addLast(id)
            else results[id] = Observation(id, Result.NOT_ADVERTISED)
        }
    }
    fun next(): ByteArray? {
        check(waiting == null)
        val id = pending.removeFirstOrNull() ?: return null
        waiting = id
        results[id] = Observation(id, Result.WAITING)
        return CcuProtocol.vehicleRequest(1248, listOf(id))
    }
    fun accept(message: CcuProtocol.Message): Boolean {
        val id = waiting ?: return false
        if (message.command != 0x10f || CcuProtocol.server(message) != 1248) return false
        val item = CcuProtocol.readings(message).firstOrNull { it.id == id } ?: return false
        results[id] = Observation(id, Result.REPLIED, item.bytes.size)
        waiting = null
        return true
    }
    fun timeout() {
        val id = waiting ?: return
        results[id] = Observation(id, Result.TIMED_OUT)
        waiting = null
    }
    fun cancel() {
        waiting?.let { results[it] = Observation(it, Result.CANCELLED) }
        pending.forEach { results[it] = Observation(it, Result.CANCELLED) }
        waiting = null; pending.clear()
    }
}
