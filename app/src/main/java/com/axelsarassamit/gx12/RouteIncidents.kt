package com.axelsarassamit.gx12

import kotlin.math.*

data class IncidentPoint(val latitude:Double,val longitude:Double) {
    fun valid()=latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 && longitude in -180.0..180.0
}
enum class IncidentKind(val label:String) { ACCIDENT("Accident"), ROAD_CLOSED("Road closed");
    companion object { fun fromCode(code:Int)=when(code){1->ACCIDENT;8->ROAD_CLOSED;else->null} }
}
data class ReportedIncident(val id:String,val kind:IncidentKind,val geometry:List<IncidentPoint>,val line:Boolean)
data class RouteIncident(val incident:ReportedIncident,val startMeters:Double,val endMeters:Double)
data class TrafficAlert(val id:String,val kind:IncidentKind,val meters:Double) {
    val distance:String get()=if(meters>=1000) String.format(java.util.Locale.US,"%.1f km",meters/1000) else "${(meters.coerceAtLeast(0.0)/50).roundToInt()*50} m"
    val speech:String get()="TomTom reports ${if(kind==IncidentKind.ACCIDENT) "an accident" else "a road closure"} near your route${if(meters>=50) " in $distance" else " here"}."
}

/** Geometric corridor matching does not establish lane, direction, elevation or road identity. */
object RouteIncidentMatcher {
    fun match(points:List<IncidentPoint>,cumulative:DoubleArray,travelled:Double,reports:List<ReportedIncident>):List<RouteIncident> {
        if(points.size<2 || points.size!=cumulative.size || !travelled.isFinite() || points.any{!it.valid()})return emptyList()
        val scale=cos(Math.toRadians(points.first().latitude))*111320.0
        val base=points.first()
        fun xy(p:IncidentPoint)=Pair((p.longitude-base.longitude)*scale,(p.latitude-base.latitude)*111320.0)
        val route=points.map(::xy)
        // The bounded search runs off the UI thread. Only the next 10 km are requested/matched.
        val edges=(0 until points.lastIndex).filter{cumulative[it+1]>=travelled && cumulative[it]<=travelled+10000}.take(2000)
        fun project(p:Pair<Double,Double>,a:Pair<Double,Double>,b:Pair<Double,Double>):Pair<Double,Double> {
            val dx=b.first-a.first;val dy=b.second-a.second;val square=dx*dx+dy*dy
            val f=if(square<.01)0.0 else (((p.first-a.first)*dx+(p.second-a.second)*dy)/square).coerceIn(0.0,1.0)
            return Pair(hypot(p.first-a.first-f*dx,p.second-a.second-f*dy),f)
        }
        return reports.take(100).mapNotNull { report ->
            if(report.geometry.isEmpty() || report.geometry.size>512 || report.geometry.any{!it.valid()})return@mapNotNull null
            val geometry=report.geometry.map(::xy);var begin=Double.POSITIVE_INFINITY;var end=Double.NEGATIVE_INFINITY
            fun hit(edge:Int,p:Pair<Double,Double>) {
                val (distance,fraction)=project(p,route[edge],route[edge+1])
                val at=cumulative[edge]+fraction*(cumulative[edge+1]-cumulative[edge])
                if(distance<=15 && at>=travelled-20 && at<=travelled+10000){begin=min(begin,at);end=max(end,at)}
            }
            for(edge in edges) {
                if(!report.line) {hit(edge,geometry.first());continue}
                val ra=route[edge];val rb=route[edge+1];val rx=rb.first-ra.first;val ry=rb.second-ra.second
                for(j in 0 until geometry.lastIndex) {
                    val a=geometry[j];val b=geometry[j+1];val dx=b.first-a.first;val dy=b.second-a.second
                    val length=hypot(rx,ry)*hypot(dx,dy)
                    // Reject crossing streets. Absolute alignment avoids assuming undocumented geometry direction.
                    if(length<1 || abs(rx*dx+ry*dy)/length<.866)continue
                    hit(edge,a);hit(edge,b)
                    if(project(ra,a,b).first<=15)hit(edge,ra)
                    if(project(rb,a,b).first<=15)hit(edge,rb)
                }
            }
            if(begin.isFinite())RouteIncident(report,begin,end)else null
        }.sortedBy{it.startMeters}
    }
}

class IncidentAnnouncements {
    private val spoken=HashMap<String,Int>()
    private var lastAt:Long?=null
    fun reset(){spoken.clear();lastAt=null}
    fun next(alert:TrafficAlert,now:Long):Boolean {
        if(!alert.meters.isFinite() || alert.meters !in 0.0..2500.0)return false
        val stage=if(alert.meters<=400)2 else 1
        return (spoken[alert.id+alert.kind.name] ?: 0)<stage && lastAt?.let{now-it>=30000}!=false
    }
    fun accepted(alert:TrafficAlert,now:Long){spoken[alert.id+alert.kind.name]=if(alert.meters<=400)2 else 1;lastAt=now}
}
