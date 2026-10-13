package app.pillion.core
import kotlin.math.*

data class RideFix(val nanos:Long,val speed:Double,val accuracy:Double,val speedAccuracy:Double)
object RideMeasurements {
    fun trusted(f:RideFix,now:Long)=f.nanos>0 && now-f.nanos in 0..3_000_000_000L &&
        f.speed.isFinite() && f.speed in 0.0..70.0 && f.accuracy.isFinite() && f.accuracy in 0.0..25.0 &&
        f.speedAccuracy.isFinite() && f.speedAccuracy in 0.0..1.5
    fun acceleration(previous:RideFix?,next:RideFix):Double? {
        if(previous==null) return null
        val seconds=(next.nanos-previous.nanos)/1e9
        if(seconds !in 1.0..5.0) return null
        val difference=next.speed-previous.speed
        if(abs(difference)<max(2.0,previous.speedAccuracy+next.speedAccuracy)) return null
        return (difference/seconds).takeIf { it.isFinite() && abs(it)<=12.0 }
    }
    fun plausible(previous:RideFix?,next:RideFix):Boolean {
        if(previous==null) return true
        val seconds=(next.nanos-previous.nanos)/1e9
        return seconds>0 && (seconds>5 || abs(next.speed-previous.speed)/seconds<=12)
    }
    private fun dot(a:DoubleArray,b:DoubleArray)=a.indices.sumOf { a[it]*b[it] }
    fun lean(up:DoubleArray,baseline:DoubleArray):Double? {
        if(up.size!=3 || baseline.size!=3 || (up+baseline).any { !it.isFinite() }) return null
        // The mounted phone faces the rider. A flat mount cannot establish the bike's forward axis.
        val forward=doubleArrayOf(baseline[2]*baseline[0],baseline[2]*baseline[1],-1+baseline[2]*baseline[2])
        val length=sqrt(dot(forward,forward));if(length<.3) return null
        for(i in forward.indices) forward[i]/=length
        val right=doubleArrayOf(forward[1]*baseline[2]-forward[2]*baseline[1],forward[2]*baseline[0]-forward[0]*baseline[2],forward[0]*baseline[1]-forward[1]*baseline[0])
        return Math.toDegrees(atan2(dot(up,right),dot(up,baseline))).takeIf { abs(it)<=75 }
    }
}
