package app.pillion.core
import kotlin.math.*

/** Requires verified units from the bike adapter. No inferred litres from gauge bars. */
data class BikeFuelReading(val remainingLitres:Double,val litresPer100Km:Double,val tankLitres:Double,val elapsedNanos:Long,val source:String)
data class FuelRangeEstimate(val kilometresToEmpty:Double,val ridingMinutesToEmpty:Double?,val kilometresToRefuel:Double)
object FuelRange {
    fun estimate(reading:BikeFuelReading?,nowNanos:Long,averageKph:Double?,reserveLitres:Double):FuelRangeEstimate? {
        val r=reading ?: return null
        if(r.source.isBlank() || nowNanos-r.elapsedNanos !in 0..30_000_000_000L ||
            !r.tankLitres.isFinite() || r.tankLitres !in 1.0..60.0 || !r.remainingLitres.isFinite() || r.remainingLitres !in 0.0..r.tankLitres ||
            !r.litresPer100Km.isFinite() || r.litresPer100Km !in .5..30.0 || !reserveLitres.isFinite() || reserveLitres !in 0.0..r.tankLitres) return null
        val kilometres=r.remainingLitres/r.litresPer100Km*100
        val minutes=averageKph?.takeIf { it.isFinite() && it in 5.0..180.0 }?.let { kilometres/it*60 }
        return FuelRangeEstimate(kilometres,minutes,max(0.0,r.remainingLitres-reserveLitres)/r.litresPer100Km*100)
    }
}
