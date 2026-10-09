package app.pillion.core

import kotlin.math.roundToLong

/** Route-provider duration weighted by progress within each instruction's geometry. */
data class RouteTimeLeg(val startMeters: Double, val endMeters: Double, val millis: Long)
object RouteTimeEstimator {
    fun remainingMillis(totalMeters: Double, totalMillis: Long, travelledMeters: Double, legs: List<RouteTimeLeg>): Long {
        if (!totalMeters.isFinite() || totalMeters <= 0 || totalMillis <= 0) return 0
        val travelled = if (travelledMeters.isFinite()) travelledMeters.coerceIn(0.0,totalMeters) else 0.0
        if (travelled >= totalMeters) return 0
        val valid = legs.filter { it.startMeters.isFinite() && it.endMeters.isFinite() && it.startMeters >= 0 && it.endMeters <= totalMeters && it.endMeters > it.startMeters && it.millis > 0 }.sortedBy { it.startMeters }
        val complete = valid.isNotEmpty() && kotlin.math.abs(valid.first().startMeters) < 0.01 &&
            kotlin.math.abs(valid.last().endMeters-totalMeters) < 0.01 &&
            valid.zipWithNext().all { (a,b) -> kotlin.math.abs(a.endMeters-b.startMeters) < 0.01 }
        // Missing or overlapping sections must not silently discard part of the journey.
        if (!complete)
            return (totalMillis.toDouble()*(1.0-travelled/totalMeters)).roundToLong().coerceIn(0,totalMillis)
        val estimatedTotal = valid.sumOf { it.millis.toDouble() }
        val estimatedLeft = valid.sumOf {
            val fraction = ((it.endMeters-travelled)/(it.endMeters-it.startMeters)).coerceIn(0.0,1.0)
            it.millis.toDouble()*fraction
        }
        return (totalMillis.toDouble()*estimatedLeft/estimatedTotal).roundToLong().coerceIn(0,totalMillis)
    }
    fun arrivalMillis(now: Long, remaining: Long): Long {
        val duration=remaining.coerceAtLeast(0)
        return if (now > 0 && duration > Long.MAX_VALUE-now) Long.MAX_VALUE else now+duration
    }
    fun durationText(millis: Long): String {
        if (millis <= 0) return "0 min"
        if (millis < 60000) return "<1 min"
        val minutes = millis/60000 + if(millis%60000>0) 1 else 0
        return if(minutes < 60) "$minutes min" else "${minutes/60} h ${minutes%60} min"
    }
}
