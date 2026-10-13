package app.pillion.core

class MapZoom {
    companion object { const val MIN_LEVEL=3; const val MAX_LEVEL=20 }
    data class State(val level:Int,val revision:Long)
    @Volatile var level = 16.0
        private set
    private var chosen = false
    private var revision=0L
    @Synchronized fun change(inside: Boolean): Double {
        chosen = true
        level = (level + if (inside) 1.0 else -1.0).coerceIn(MIN_LEVEL.toDouble(), MAX_LEVEL.toDouble())
        revision++ // A request at the boundary still needs dashboard feedback.
        return level
    }
    @Synchronized fun navigationStarted() { if (!chosen) { level = 17.0;revision++ } }
    @Synchronized fun state()=State(level.toInt(),revision)
}
