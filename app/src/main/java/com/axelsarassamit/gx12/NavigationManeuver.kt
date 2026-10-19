package com.axelsarassamit.gx12

import java.util.Locale

/** Provider maneuver types, never inferred from localized instruction text. */
object NavigationManeuver {
    private fun key(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("[\\s_-]+"), "_")
    fun tomTom(type: String, modifier: String = "", drivingSide: String = ""): Int {
        val normalized = key(type)
        val compact = normalized.replace("_", "")
        if (compact in setOf("MAKEUTURN", "TRYMAKEUTURN", "UTURN", "UTURNLEFT", "UTURNRIGHT", "MAKEUTURNLEFT", "MAKEUTURNRIGHT", "LEFTUTURN", "RIGHTUTURN"))
            return when {
                compact.endsWith("RIGHT") || compact.startsWith("RIGHT") || key(modifier) == "RIGHT" -> 8
                compact.endsWith("LEFT") || compact.startsWith("LEFT") || key(modifier) == "LEFT" -> -8
                key(drivingSide) == "LEFT" -> 8 // Left-side traffic normally turns across the road to the right.
                else -> -8
            }
        return when (normalized) {
            "TURN_LEFT" -> -2; "TURN_RIGHT" -> 2
            "BEAR_LEFT", "KEEP_LEFT" -> -1; "BEAR_RIGHT", "KEEP_RIGHT" -> 1
            "SHARP_LEFT" -> -3; "SHARP_RIGHT" -> 3
            "ARRIVE", "ARRIVE_LEFT", "ARRIVE_RIGHT" -> 5
            "ENTER_ROUNDABOUT", "ROUNDABOUT_CROSS", "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT", "ROUNDABOUT_BACK" -> 6
            "EXIT_ROUNDABOUT" -> 7
            else -> 0
        }
    }
    fun valhalla(type: Int): Int = when(type) {
        9 -> 1; 10,18,20,23 -> 2; 11 -> 3; 12 -> 8
        13 -> -8; 14 -> -3; 15,19,21,24 -> -2; 16 -> -1
        4,5,6 -> 5; 26 -> 6; 27 -> 7; else -> 0
    }
    fun glyph(sign: Int): String = when {
        sign == -8 -> "↶ U"; sign == 8 -> "↷ U"
        sign == 6 -> "↻"; sign == 5 -> "●"; sign < 0 -> "←"
        sign in 1..3 || sign == 7 -> "→"; else -> "↑"
    }
}
