package com.axelsarassamit.gx12

/** Stateful speech scheduling, separate from GPS, Android and the map renderer. */
class NavigationAnnouncements {
    data class Cue(val id: String, val text: String, internal val turn: Int = -1,
        internal val stage: Int = -1, internal val meters: Double = 0.0)
    private val stages = HashMap<Int, Int>()
    private var startPending = true
    private var lastAt: Long? = null
    private var lastTurn = -1
    private var lastMeters = 0.0
    private var arrived = false
    private var offRouteReported = false
    private var gpsLostReported = false
    private var startText = "Navigation started."

    fun reset(announceStart: Boolean = true, recalculated: Boolean = false) {
        stages.clear(); startPending = announceStart; lastAt = null; lastTurn = -1
        lastMeters = 0.0; arrived = false; offRouteReported = false; gpsLostReported = false
        startText = if(recalculated) "Route recalculated." else "Navigation started."
    }
    fun onTrack() { offRouteReported = false; gpsLostReported = false }
    fun offRoute(): Cue? = if (arrived || offRouteReported) null else Cue("off-route", "You are off route. Recalculating your route.")
    fun gpsLost(): Cue? = if (arrived || gpsLostReported) null else Cue("gps-lost", "GPS signal lost. Waiting for your position.")

    fun next(turn: Int, sign: Int, meters: Double, road: String, speedMps: Double,
             arriving: Boolean, now: Long, following: String = ""): Cue? {
        if (arrived || !meters.isFinite() || meters < 0) return null
        if (arriving) return Cue("arrival", "You have arrived at your destination.")
        val speed = speedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 60.0) ?: 0.0
        val prepare = (speed * 30).coerceIn(300.0, 1000.0)
        val approach = (speed * 12).coerceIn(100.0, 400.0)
        val immediate = (speed * 3).coerceIn(20.0, 80.0)
        val stage = when { meters <= immediate -> 3; meters <= approach -> 2; meters <= prepare -> 1; else -> 0 }
        val previous = stages[turn] ?: -1
        val elapsed = lastAt?.let { now - it } ?: Long.MAX_VALUE
        if (stage > 0) {
            // Only the most relevant threshold is spoken after a GPS gap or a late route start.
            if (stage <= previous || (elapsed < 6000 && stage != 3)) return null
            val instruction = if (stage == 3) NavigationSpeech.now(sign, road)
                else NavigationSpeech.prompt(sign, meters, road)
            val prefix = if(startPending && stage != 3) "$startText " else ""
            return Cue("turn-$turn-$stage", prefix + instruction + following.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(), turn, stage, meters)
        }
        if (startPending) return Cue("start-$turn", "$startText ${NavigationSpeech.prompt(sign, meters, road)}", turn, 0, meters)
        if (previous < 0 && lastTurn >= 0 && turn != lastTurn && elapsed >= 6000)
            return Cue("leg-$turn", NavigationSpeech.prompt(sign, meters, road), turn, 0, meters)
        // Reassurance on long stretches, only after real forward progress. Never repeat while parked.
        if (elapsed >= 180000 && turn == lastTurn && lastMeters - meters >= 1000 && previous <= 0)
            return Cue("continue-$turn-$now", NavigationSpeech.prompt(sign, meters, road), turn, 0, meters)
        return null
    }
    fun accepted(cue: Cue, now: Long) {
        when (cue.id) {
            "arrival" -> arrived = true
            "off-route" -> offRouteReported = true
            "gps-lost" -> gpsLostReported = true
            else -> {
                stages[cue.turn] = maxOf(stages[cue.turn] ?: -1, cue.stage)
                startPending = false; lastTurn = cue.turn; lastMeters = cue.meters
            }
        }
        lastAt = now
    }
}
