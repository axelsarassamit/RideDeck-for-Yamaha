package com.axelsarassamit.gx12

import kotlin.math.*

data class RoutePoint(val latitude:Double,val longitude:Double)
data class RouteOption(val meters:Double,val millis:Long,val points:List<RoutePoint>)
/** Compare shape, not vertex lists: providers can encode the same road at different density. */
object RouteAlternatives {
    fun indices(options:List<RouteOption>):List<Int> {
        val selected=mutableListOf<Int>()
        options.indices.sortedWith(compareBy<Int>{options[it].millis}.thenBy{options[it].meters}).forEach { i ->
            val candidate=options[i]
            if(candidate.millis>=0 && candidate.meters.isFinite() && candidate.meters>=0 && candidate.points.size>=2 &&
                selected.none { sameRoads(candidate,options[it]) }) selected.add(i)
        }
        return selected.take(3)
    }
    private fun sameRoads(a:RouteOption,b:RouteOption):Boolean {
        if(abs(a.meters-b.meters)>max(50.0,min(a.meters,b.meters)*0.03)) return false
        return overlap(a.points,b.points)>=0.95 && overlap(b.points,a.points)>=0.95
    }
    private fun overlap(a:List<RoutePoint>,b:List<RoutePoint>):Double {
        val scale=cos(Math.toRadians(a.first().latitude));fun xy(p:RoutePoint)=doubleArrayOf(p.longitude*111195*scale,p.latitude*111195)
        val vertices=a.map(::xy);val lengths=DoubleArray(a.size)
        for(i in 1 until a.size)lengths[i]=lengths[i-1]+hypot(vertices[i][0]-vertices[i-1][0],vertices[i][1]-vertices[i-1][1])
        // Limit work on very dense provider geometry. Retain the final endpoint.
        val step=max(1,(b.size-1+2047)/2048)
        val line=(b.indices.step(step).map{xy(b[it])}+listOf(xy(b.last())))
        var hits=0;var segment=1
        for(n in 0..64) {
            val at=lengths.last()*n/64
            while(segment<lengths.lastIndex && lengths[segment]<at)segment++
            val fraction=if(lengths[segment]>lengths[segment-1]) (at-lengths[segment-1])/(lengths[segment]-lengths[segment-1]) else 0.0
            val x=vertices[segment-1][0]+fraction*(vertices[segment][0]-vertices[segment-1][0]);val y=vertices[segment-1][1]+fraction*(vertices[segment][1]-vertices[segment-1][1])
            if((1 until line.size).any { i ->
                val u=line[i-1];val v=line[i];val dx=v[0]-u[0];val dy=v[1]-u[1];val sq=dx*dx+dy*dy
                val t=if(sq>0) (((x-u[0])*dx+(y-u[1])*dy)/sq).coerceIn(0.0,1.0) else 0.0
                hypot(x-u[0]-t*dx,y-u[1]-t*dy)<=25
            }) hits++
        }
        return hits/65.0
    }
}

object RouteChoices {
    fun ranked(routes:List<NavigationRoute>):List<NavigationRoute> = RouteAlternatives.indices(routes.map { r ->
        RouteOption(r.meters,r.millis,r.points.map{RoutePoint(it.latitude,it.longitude)})
    }).map{routes[it]}
    fun label(routes:List<NavigationRoute>,index:Int):String {
        val route=routes[index]
        val label=when { index==0 && route.millis!=routes.minOf{it.millis} -> "Preferred";route.millis==routes.minOf{it.millis} -> "Fastest";route.meters==routes.minOf{it.meters}->"Shortest";else->"Alternative" }
        return "${index+1} · $label"
    }
    fun comparison(routes:List<NavigationRoute>,index:Int):String {
        val route=routes[index]
        val extra=((route.millis-routes.minOf{it.millis}+59999)/60000).coerceAtLeast(0)
        val time=if(extra==0L) "Best ETA" else "+$extra min"
        val turns=route.turns.count{ kotlin.math.abs(it.sign)==8 }
        return "$time · $turns U-turns" + if(route.trafficDelayMillis>=60000) " · traffic +${(route.trafficDelayMillis+59999)/60000} min" else ""
    }
}
