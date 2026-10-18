package com.axelsarassamit.gx12

import app.pillion.core.*
import org.json.JSONArray

object LaneData {
    fun valhalla(raw:JSONArray?):List<GuidanceLane> = (if(raw!=null && raw.length() in 1..8) (0 until raw.length()).toList() else emptyList()).mapNotNull { i ->
        val lane=raw?.optJSONObject(i)?:return@mapNotNull null
        val directions=LaneGuidance.mask(lane.optInt("directions"))
        GuidanceLane(directions,LaneGuidance.mask(lane.optInt("active")).filter{it in directions},LaneGuidance.mask(lane.optInt("valid")).filter{it in directions})
    }
    fun tomTom(raw:JSONArray?,pointCount:Int):List<LaneSection> = (0 until (raw?.length()?:0)).mapNotNull { i ->
        val section=raw?.optJSONObject(i)?:return@mapNotNull null
        if(section.optString("sectionType")!="LANES")return@mapNotNull null
        val a=section.optInt("startPointIndex",-1);val b=section.optInt("endPointIndex",-1)
        if(a !in 0 until pointCount || b !in a until pointCount)return@mapNotNull null
        val lanes=section.optJSONArray("lanes")?:return@mapNotNull null
        if(lanes.length() !in 1..8)return@mapNotNull null
        LaneSection(a,b,(0 until lanes.length()).map { n->
            val lane=lanes.getJSONObject(n);val directions=lane.optJSONArray("directions")
            val values=(0 until (directions?.length()?:0)).mapNotNull { LaneGuidance.direction(directions!!.optString(it)) }
            val follow=LaneGuidance.direction(lane.optString("follow"))?.takeIf{it in values}
            GuidanceLane(values,follow?.let{listOf(it)}?:emptyList())
        }).takeIf{it.usable()}
    }
}
