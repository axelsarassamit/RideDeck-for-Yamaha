package app.pillion.core

/** Experimental MyRide-style 1 Hz sample model. Never a fuel gauge or tank subtraction. */
class SampledFuelSession(private val bike: String, private val initialOdometer: Double, private val startedAt: Long, private val deliveryVerified: Boolean = false) {
    data class Result(val samples: Int, val coveredMillis: Long, val litres: Double?, val kmPerLitre: Double?, val reason: String?)
    private var lastAt: Long? = null
    private var lastOdometer = initialOdometer
    private var rawSum = 0L
    private var samples = 0
    private var reason: String? = null
    init { require(bike.isNotBlank() && initialOdometer.isFinite() && initialOdometer >= 0 && startedAt >= 0) }
    fun invalidate(why: String) { if (reason == null) reason = why }
    fun sample(identity: String, now: Long, raw: ByteArray, odometer: Double?) {
        if (reason != null) return
        if (identity != bike) { invalidate("Bike changed"); return }
        val previous = lastAt
        // The envelope supplies no sample identity. Bursts, duplicate delivery and gaps cannot be repaired.
        if (now < startedAt || (previous == null && now - startedAt > 2000) ||
            (previous != null && now - previous !in 700L..1300L)) {
            invalidate("Sample timing is incomplete or ambiguous; restart the test"); return
        }
        if (raw.size < 4) { invalidate("Incomplete fuel sample"); return }
        if (odometer == null || !odometer.isFinite() || odometer < lastOdometer || odometer - lastOdometer > 0.10001) {
            invalidate("Fresh consistent bike odometer required"); return
        }
        rawSum += ((raw[2].toInt() and 255) shl 8) or (raw[3].toInt() and 255)
        lastAt = now; lastOdometer = odometer; samples++
    }
    fun result(now: Long): Result {
        if (reason == null && now - (lastAt ?: startedAt) > 2000) invalidate("Fuel samples stopped; restart the test")
        val duration = (lastAt?.minus(startedAt) ?: 0).coerceAtLeast(0)
        // Require a sustained cadence, not a single snapshot. Raw units remain calibration-pending.
        val litres = if (reason == null && deliveryVerified && samples >= 5) rawSum * 0.00001 else null
        val distance = lastOdometer - initialOdometer
        val economy = litres?.takeIf { it > 0 && distance >= 0.1 }?.let { distance / it }
        return Result(samples, duration, litres, economy, reason ?: if (!deliveryVerified) "Total unavailable: firmware sample identity and delivery cadence need verification" else if (samples < 5) "Waiting for five regular 1 Hz samples" else null)
    }
}
