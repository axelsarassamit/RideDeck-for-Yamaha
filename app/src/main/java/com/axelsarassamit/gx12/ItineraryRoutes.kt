package com.axelsarassamit.gx12

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ordered, local-only itinerary. Reaching a routed leg end retires that stop. */
object ItineraryRoutes {
    fun remaining(route:NavigationRoute,progress:Int):List<NavigationPlace> =
        if(route.itinerary.isEmpty()) listOf(route.destination) else route.itinerary.filterIndexed { index,_ -> index==route.itinerary.lastIndex || progress<route.stopIndices.getOrElse(index){Int.MAX_VALUE} }
    fun combine(target:NavigationPlace,parts:List<NavigationRoute>):NavigationRoute {
        require(parts.isNotEmpty())
        val limits=mutableListOf<Int>();val hasLimits=parts.any{it.speedLimits!=null}
        val points=mutableListOf<org.maplibre.android.geometry.LatLng>();val turns=mutableListOf<NavigationTurn>();val lanes=mutableListOf<app.pillion.core.LaneSection>();val ends=mutableListOf<Int>()
        parts.forEachIndexed { index,part ->
            val overlap=points.lastOrNull()==part.points.firstOrNull()
            val offset=points.size-if(overlap)1 else 0
            if(points.isNotEmpty() && !overlap) limits.add(0)
            val partLimits=part.speedLimits?.takeIf{it.size==part.points.size-1}
            limits.addAll((0 until part.points.lastIndex).map{partLimits?.get(it) ?: 0})
            points.addAll(if(overlap)part.points.drop(1) else part.points)
            part.turns.forEach { turn ->
                // Intermediate arrivals are via points, not the end of navigation.
                if(index==parts.lastIndex || (turn.start<part.points.lastIndex && turn.sign!=5)) turns.add(turn.copy(start=turn.start+offset,end=turn.end+offset))
            }
            lanes.addAll(part.lanes.map{it.copy(start=it.start+offset,end=it.end+offset)})
            ends.add(points.lastIndex)
        }
        require(points.size<=100000)
        return NavigationRoute(target,points,turns,parts.sumOf{it.meters},parts.sumOf{it.millis},parts.first().provider,speedLimits=if(hasLimits)limits.toIntArray() else null,trafficDelayMillis=parts.sumOf{it.trafficDelayMillis},lanes=lanes,stopIndices=ends)
    }
    fun encode(stops:List<NavigationPlace>)=JSONArray().apply { stops.forEach { put(JSONObject().put("label",it.label.take(300)).put("lat",it.latitude).put("lon",it.longitude)) } }.toString()
    fun decode(text:String?):List<NavigationPlace> = runCatching {
        val a=JSONArray(text ?: "[]");require(a.length()<=6)
        (0 until a.length()).map { val p=a.getJSONObject(it);val lat=p.getDouble("lat");val lon=p.getDouble("lon");require(lat in -90.0..90.0 && lon in -180.0..180.0);NavigationPlace(p.optString("label","Stop"),lat,lon) }
    }.getOrDefault(emptyList())
}
