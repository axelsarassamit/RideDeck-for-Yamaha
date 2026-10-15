package com.axelsarassamit.gx12

import kotlin.math.*

/** Monotonic-time quality gate. No invented positions, speeds or road snapping. */
data class NavigationFix(val latitude:Double,val longitude:Double,val accuracy:Double,val atNanos:Long,val satellite:Boolean)
object NavigationFixQuality {
    fun accept(next:NavigationFix, previous:NavigationFix?, nowNanos:Long):Boolean {
        if(next.latitude !in -90.0..90.0 || next.longitude !in -180.0..180.0 ||
            !next.accuracy.isFinite() || next.accuracy !in 0.0..50.0 || nowNanos-next.atNanos !in 0..10_000_000_000L) return false
        previous ?: return true
        val seconds=(next.atNanos-previous.atNanos)/1e9
        if(seconds<=0) return false
        // A fresh satellite fix should not alternate with less precise Wi-Fi/cell positions.
        if(previous.satellite && !next.satellite && seconds<5 && next.accuracy>previous.accuracy*0.5) return false
        // Allow reacquisition after a gap, but reject single impossible jumps while tracking.
        if(seconds<=10 && distance(previous,next)>80*seconds+previous.accuracy+next.accuracy) return false
        return true
    }
    fun speed(speed:Double?, uncertainty:Double?):Double? = speed?.takeIf {
        it.isFinite() && it in 0.0..80.0 && (uncertainty==null || (uncertainty.isFinite() && uncertainty in 0.0..2.0))
    }
    fun heading(speed:Double?, bearing:Double?, uncertainty:Double?):Double? = bearing?.takeIf {
        it.isFinite() && it in 0.0..360.0 && (speed ?: 0.0)>=2.5 &&
            (uncertainty==null || (uncertainty.isFinite() && uncertainty in 0.0..30.0))
    }
    private fun distance(a:NavigationFix,b:NavigationFix):Double {
        val dLat=Math.toRadians(b.latitude-a.latitude);val dLon=Math.toRadians(b.longitude-a.longitude)
        val h=sin(dLat/2).pow(2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(dLon/2).pow(2)
        return 6371000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
}
