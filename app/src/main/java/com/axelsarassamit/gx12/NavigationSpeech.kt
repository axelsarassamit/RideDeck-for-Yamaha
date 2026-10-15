package com.axelsarassamit.gx12

import java.util.Locale
import kotlin.math.roundToInt

/** English guidance is independent of message dictation and the phone's system locale. */
object NavigationSpeech {
    val locale: Locale = Locale.US
    fun acceptsVoice(language: String) = language == "en" || language == "eng"
    fun prompt(sign: Int, meters: Double, road: String, arriving: Boolean = false): String {
        if (arriving) return "Arriving at your destination."
        val safe = meters.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val distance = if (safe >= 1000) String.format(Locale.US, "%.1f kilometers", safe / 1000)
            else "${(safe / 10).roundToInt() * 10} meters"
        return "In $distance, ${action(sign,road)}."
    }
    fun now(sign: Int, road: String): String = "Now, ${action(sign,road)}."
    fun following(sign: Int, road: String): String = "Then, ${action(sign,road)}."
    private fun action(sign: Int, road: String): String {
        val action = when (sign) {
            -8, 8 -> "make a U-turn"
            -3 -> "turn sharply left"; -2 -> "turn left"; -1 -> "bear left"
            1 -> "bear right"; 2 -> "turn right"; 3 -> "turn sharply right"
            5 -> "arrive at your destination"; 6 -> "enter the roundabout"
            7 -> "exit the roundabout"; else -> "continue straight"
        }
        // Google speech can switch languages for Thai text even with an English voice.
        // Do not guess a translation. Preserve the original road on screen.
        val spokenRoad = road.trim().take(120).takeIf { name ->
            name.isNotBlank() && name.any { it in 'A'..'Z' || it in 'a'..'z' } &&
                name.all { !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
        }
        val roadSuffix = if(sign in listOf(5,6,7)) "" else spokenRoad?.let { " onto $it" }.orEmpty()
        return "$action$roadSuffix"
    }
}
