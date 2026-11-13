package com.axelsarassamit.gx12

import app.pillion.core.LaneSection

/** Presentation only. Lane recommendations come from the route provider, not sensed occupancy. */
object BikeDisplayPresentation {
    data class Footer(val label:String,val value:String)
    fun junction(wasVisible:Boolean, lanes:LaneSection?, meters:Double?, fresh:Boolean):Boolean =
        fresh && lanes?.usable()==true && meters!=null && meters.isFinite() &&
            meters in 0.0..(if(wasVisible)350.0 else 300.0)

    fun footer(arrival:String?, range:String?, elapsedMs:Long):Footer {
        val eta=arrival?.takeIf{it.isNotBlank()}
        val fuel=range?.takeIf{it.isNotBlank()}
        val fuelPhase=fuel!=null && (eta==null || (elapsedMs.coerceAtLeast(0)/10_000)%2==1L)
        return when {
            fuelPhase -> Footer("Est. range",fuel!!.removePrefix("Est. fuel range "))
            eta!=null -> Footer("Arrival",eta)
            else -> Footer("ETA / range","—")
        }
    }
}
