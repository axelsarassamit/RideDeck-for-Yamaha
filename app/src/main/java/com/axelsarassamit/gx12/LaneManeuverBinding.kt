package com.axelsarassamit.gx12

import app.pillion.core.LaneSection

/** Bind lane recommendations to one instruction before giving them that instruction's distance. */
object LaneManeuverBinding {
    fun associatedPoint(section:LaneSection,distances:DoubleArray,turns:List<Int>,anchorsAtStart:Boolean):Int? {
        if(!section.usable()||section.start !in distances.indices||section.end !in distances.indices)return null
        val valid=turns.filter{it in distances.indices}
        // Valhalla embeds lanes in a maneuver. TomTom describes sections ending at a junction,
        // or at a stop line at most 100m before its next instruction.
        val point=if(anchorsAtStart)section.start else valid.lastOrNull{it in section.start..section.end}
            ?:valid.firstOrNull{it>section.end && distances[it]-distances[section.end] in 0.0..100.0}
        return point?.takeIf{valid.count{candidate->candidate==it}==1}
    }
    fun select(sections:List<LaneSection>,distances:DoubleArray,turns:List<Int>,activePoint:Int,
        travelled:Double,anchorsAtStart:Boolean):LaneSection? {
        if(activePoint !in distances.indices||!travelled.isFinite()||travelled>distances[activePoint])return null
        return sections.filter{associatedPoint(it,distances,turns,anchorsAtStart)==activePoint}
            .minByOrNull{it.end-it.start}
    }
}
