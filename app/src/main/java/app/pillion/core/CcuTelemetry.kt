package app.pillion.core

enum class BikeMetric(val label: String, val unit: String) {
    RPM("Engine speed", "rpm"), SPEED("Bike speed", "km/h"), VOLTAGE("Bike battery", "V"),
    COOLANT("Coolant temperature", "°C"), AIR("Intake air temperature", "°C"),
    THROTTLE("Throttle opening", "%"), ODOMETER("Odometer", "km")
}
data class BikeValue(val metric: BikeMetric, val value: Double, val elapsedMs: Long, val wallMs: Long,
                     val source: String = "Yamaha CCU · xmax-1248-v1") {
    fun fresh(now: Long) = now - elapsedMs in 0..5000
}
/** Source-confirmed offsets for the observed 33-byte XMAX local record. */
object CcuTelemetry {
    fun decode(message: CcuProtocol.Message, elapsedMs: Long, wallMs: Long): List<BikeValue> {
        if (message.command != 0x10f || CcuProtocol.server(message) != 1248) return emptyList()
        val out = mutableListOf<BikeValue>()
        fun add(metric: BikeMetric, value: Double) { if (value.isFinite()) out.add(BikeValue(metric, value, elapsedMs, wallMs)) }
        for (item in CcuProtocol.readings(message)) {
            val data = item.bytes
            if (item.id == 1 && data.size == 33) {
                fun byte(index: Int) = data[index].toInt() and 255
                if (byte(0) != 255 || byte(1) != 255) add(BikeMetric.RPM, ((byte(0) shl 8) or byte(1)).toDouble())
                if (byte(2) != 255) add(BikeMetric.SPEED, byte(2).toDouble())
                if (byte(6) != 0 && byte(6) != 255) add(BikeMetric.VOLTAGE, byte(6) * 18.75 / 256)
                if (byte(8) != 255) add(BikeMetric.THROTTLE, byte(8) * 125.0 / 256)
                if (byte(14) != 0 && byte(14) != 255) add(BikeMetric.COOLANT, (byte(14) - 30).toDouble())
                if (byte(15) != 0 && byte(15) != 255) add(BikeMetric.AIR, (byte(15) - 30).toDouble())
            } else if (item.id == 72 && data.size == 4) {
                var value = 0L; for (b in data) value = (value shl 8) or (b.toLong() and 255)
                if (value != 0xffffffffL) add(BikeMetric.ODOMETER, value / 10.0)
            }
        }
        return out
    }
    data class Fault(val server: Int, val code: Int, val status: Int)
    fun faults(message: CcuProtocol.Message): List<Fault> {
        require(message.command == 0x106)
        return CcuProtocol.fields(message.payload).filter { it.number == 3 && it.bytes != null }.map { item ->
            val fields = CcuProtocol.fields(item.bytes!!)
            fun value(n: Int) = fields.firstOrNull { it.number == n }?.integer ?: 0L
            require(value(1) in 0..65535 && value(2) in 0..65535 && value(3) in 0..4)
            Fault(value(1).toInt(), value(2).toInt(), value(3).toInt())
        }.filter { it.server == 1248 }
    }
    fun injectionCount(message: CcuProtocol.Message): Long? {
        if (message.command != 0x120 || message.payload.size != 4) return null
        var value = 0L; for (b in message.payload) value = (value shl 8) or (b.toLong() and 255)
        return value
    }
}
