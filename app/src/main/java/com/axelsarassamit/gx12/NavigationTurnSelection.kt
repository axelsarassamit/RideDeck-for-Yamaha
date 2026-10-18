package com.axelsarassamit.gx12

/** Select by measured route distance so a maneuver is not lost at its shape point. */
object NavigationTurnSelection {
    data class Turn(val meters: Double, val sign: Int)
    fun next(turns: List<Turn>, travelled: Double): Int {
        if(turns.isEmpty()) return -1
        val position=travelled.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        for(index in turns.indices) {
            val turn=turns[index]
            if(turn.sign == 0 && turn.meters <= 0.0) continue
            // Keep the immediate cue briefly after the point, but not across a nearby next turn.
            val gap=turns.getOrNull(index+1)?.meters?.minus(turn.meters)?.coerceAtLeast(0.0)
            val tolerance=gap?.let { minOf(8.0,it/2) } ?: 8.0
            if(position <= turn.meters+tolerance) return index
        }
        return turns.lastIndex
    }
}
