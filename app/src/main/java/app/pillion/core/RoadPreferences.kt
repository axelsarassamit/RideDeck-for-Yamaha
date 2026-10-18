package app.pillion.core

/** Provider-supported preferences are soft requests, never road-access permissions. */
data class RoadPreferences(val avoidMotorways:Boolean=true,val avoidTolls:Boolean=true,
    val avoidFerries:Boolean=false,val avoidDirt:Boolean=true,val fewerUTurns:Boolean=false,
    val shortest:Boolean=false,val laneGuidance:Boolean=true) {
    fun tomTomAvoids():List<String> = listOfNotNull(
        "motorways".takeIf{avoidMotorways},"tollRoads".takeIf{avoidTolls},
        "ferries".takeIf{avoidFerries},"unpavedRoads".takeIf{avoidDirt})
    fun valhallaOptions():Map<String,Any> = mapOf("use_highways" to if(avoidMotorways)0.0 else 0.5,
        "use_tolls" to if(avoidTolls)0.0 else 0.5,"use_ferry" to if(avoidFerries)0.0 else 0.5,
        "exclude_unpaved" to avoidDirt,"shortest" to shortest)
}
