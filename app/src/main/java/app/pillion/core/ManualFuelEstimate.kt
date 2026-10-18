package app.pillion.core

/** Explicit refill model. Never interprets raw CCU injection values as litres. */
data class ManualFuelAnchor(val bike:String,val fullOdometer:Double,val tankLitres:Double,val kmPerLitre:Double,
    val bufferLitres:Double,val partialLitres:Double=0.0,val lastRefillOdometer:Double=fullOdometer,
    val recordedAt:Long=0,val invalid:Boolean=false)
data class ManualFuelResult(val remainingLitres:Double,val refuelKm:Double,val distanceKm:Double)
object ManualFuelEstimate {
    fun valid(a:ManualFuelAnchor)=a.bike.isNotBlank() && a.fullOdometer.isFinite() && a.fullOdometer>=0 &&
        a.tankLitres.isFinite() && a.tankLitres in 1.0..60.0 && a.kmPerLitre.isFinite() && a.kmPerLitre in 3.0..100.0 &&
        a.bufferLitres.isFinite() && a.bufferLitres>=0 && a.bufferLitres<a.tankLitres &&
        a.partialLitres.isFinite() && a.partialLitres>=0 && a.lastRefillOdometer.isFinite() && a.lastRefillOdometer>=a.fullOdometer && !a.invalid
    fun estimate(a:ManualFuelAnchor,odometer:Double,bike:String):ManualFuelResult? {
        if(!valid(a)||bike!=a.bike||!odometer.isFinite()||odometer<a.lastRefillOdometer)return null
        val distance=odometer-a.fullOdometer
        val remaining=a.tankLitres+a.partialLitres-distance/a.kmPerLitre
        if(remaining>a.tankLitres+0.01 || distance>(a.tankLitres+a.partialLitres)*a.kmPerLitre*2)return null
        return ManualFuelResult(remaining.coerceAtLeast(0.0),((remaining-a.bufferLitres)*a.kmPerLitre).coerceAtLeast(0.0),distance)
    }
    fun partial(a:ManualFuelAnchor,odometer:Double,litres:Double):ManualFuelAnchor? {
        val before=estimate(a,odometer,a.bike)?:return null
        if(!litres.isFinite()||litres<=0||before.remainingLitres+litres>a.tankLitres)return null
        return a.copy(partialLitres=a.partialLitres+litres,lastRefillOdometer=odometer)
    }
    /** New full fill closes an interval. Partial fills belong in its denominator. */
    fun nextFull(a:ManualFuelAnchor,odometer:Double,litres:Double,now:Long):ManualFuelAnchor? {
        if(!valid(a)||!odometer.isFinite()||odometer<a.lastRefillOdometer||!litres.isFinite()||litres<=0||litres>a.tankLitres)return null
        val distance=odometer-a.fullOdometer
        if(distance<10)return null
        val measured=distance/(a.partialLitres+litres)
        if(measured !in 3.0..100.0)return null
        return a.copy(fullOdometer=odometer,kmPerLitre=measured,partialLitres=0.0,lastRefillOdometer=odometer,recordedAt=now)
    }
}
