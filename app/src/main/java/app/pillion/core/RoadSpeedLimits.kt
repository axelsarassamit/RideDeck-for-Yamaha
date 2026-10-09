package app.pillion.core

data class SpeedLimitEdge(val begin: Int, val end: Int, val limit: Double?)
data class SpeedLimitPoint(val latitude: Double, val longitude: Double)
data class SpeedLimitWindow(val start: Int, val limits: IntArray) {
    val end: Int get() = start + limits.size
    fun at(segment: Int): Int? = limits.getOrNull(segment-start)?.takeIf { it > 0 }
}

object RoadSpeedLimits {
    // Valhalla's unknown/unlimited values are not numeric posted limits.
    fun numeric(value: Double?): Int? = value?.takeIf { it.isFinite() && it in 1.0..254.0 && it == kotlin.math.floor(it) }?.toInt()
    fun segments(pointCount: Int, edges: List<SpeedLimitEdge>): IntArray {
        require(pointCount in 2..1000)
        val limits=IntArray(pointCount-1)
        val seen=BooleanArray(limits.size)
        edges.forEach { edge ->
            if(edge.begin < 0 || edge.end <= edge.begin || edge.end >= pointCount) return@forEach
            val limit=numeric(edge.limit) ?: 0
            for(index in edge.begin until edge.end) {
                limits[index]=if(seen[index]) 0 else limit
                seen[index]=true
            }
        }
        return limits
    }
    fun align(requested: List<SpeedLimitPoint>, matched: List<SpeedLimitPoint>, limits: IntArray): IntArray {
        require(requested.size in 2..401 && matched.size in 2..1000 && limits.size == matched.size-1)
        fun same(a: SpeedLimitPoint,b: SpeedLimitPoint) = kotlin.math.abs(a.latitude-b.latitude) < 0.0000011 && kotlin.math.abs(a.longitude-b.longitude) < 0.0000011
        fun groups(points: List<SpeedLimitPoint>): Pair<List<SpeedLimitPoint>,IntArray> {
            val unique=ArrayList<SpeedLimitPoint>()
            val indexes=IntArray(points.size)
            points.forEachIndexed { index,point ->
                if(unique.isEmpty() || !same(unique.last(),point)) unique.add(point)
                indexes[index]=unique.lastIndex
            }
            return unique to indexes
        }
        val (input,inputGroups)=groups(requested)
        val (output,outputGroups)=groups(matched)
        require(input.size >= 2 && input.size == output.size && input.zip(output).all { (a,b) -> same(a,b) }) { "Speed-limit geometry differs from the route." }
        val groupLimits=IntArray(input.size-1)
        for(index in limits.indices) if(outputGroups[index+1] > outputGroups[index]) groupLimits[outputGroups[index]]=limits[index]
        return IntArray(requested.size-1) { index -> if(inputGroups[index+1] > inputGroups[index]) groupLimits[inputGroups[index]] else 0 }
    }
    fun visible(fresh: Boolean, recalculating: Boolean, accuracy: Float, roadDistance: Double, headingDifference: Double): Boolean =
        fresh && !recalculating && accuracy.isFinite() && accuracy in 0f..20f &&
            roadDistance.isFinite() && roadDistance in 0.0..20.0 && headingDifference.isFinite() && headingDifference in 0.0..60.0
}
