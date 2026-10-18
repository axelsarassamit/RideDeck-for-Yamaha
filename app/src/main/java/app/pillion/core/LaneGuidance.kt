package app.pillion.core

/** Left-to-right provider lanes. A turn direction alone never creates lane guidance. */
data class GuidanceLane(val directions:List<Int>,val preferred:List<Int> = emptyList(),val valid:List<Int> = emptyList())
data class LaneSection(val start:Int,val end:Int,val lanes:List<GuidanceLane>) {
    fun usable()=start>=0 && end>=start && lanes.size in 1..8 && lanes.any {it.preferred.isNotEmpty()||it.valid.isNotEmpty()}
    fun spoken():String {
        val best=lanes.indices.filter{lanes[it].preferred.isNotEmpty()}.ifEmpty{lanes.indices.filter{lanes[it].valid.isNotEmpty()}}
        if(best.isEmpty())return ""
        return when {
            best.size==lanes.size -> "Any of these lanes follows your route."
            best==listOf(0) -> "Use the left lane."
            best==listOf(lanes.lastIndex) -> "Use the right lane."
            best==listOf(1) && lanes.size==3 -> "Use the middle lane."
            else -> "Use lanes ${best.joinToString(" and "){(it+1).toString()}} from the left."
        }
    }
}
object LaneGuidance {
    fun select(sections:List<LaneSection>,distances:DoubleArray,travelled:Double,lookAhead:Double):LaneSection? {
        if(!travelled.isFinite()||!lookAhead.isFinite())return null
        return sections.filter { it.usable() && it.end in distances.indices &&
            distances[it.end]>=travelled && distances[it.start]-travelled<=lookAhead.coerceIn(150.0,700.0) }
            .minByOrNull{distances[it.start]}
    }
    fun mask(bits:Int):List<Int> = listOf(2 to 0,4 to -3,8 to -2,16 to -1,32 to 1,64 to 2,128 to 3,256 to -8)
        .filter{(bit,_)->bits and bit != 0}.map{it.second}
    fun direction(value:String):Int?=when(value.uppercase(java.util.Locale.ROOT)) {
        "STRAIGHT"->0;"SLIGHT_LEFT"->-1;"LEFT"->-2;"SHARP_LEFT"->-3;"LEFT_U_TURN"->-8
        "SLIGHT_RIGHT"->1;"RIGHT"->2;"SHARP_RIGHT"->3;"RIGHT_U_TURN"->8;else->null
    }
}
