package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NavigationSpeechTest {
    @Test fun uTurnDoesNotBecomeASharpTurn() {
        for (sign in listOf(-8, 8)) assertEquals("In 150 meters, make a U-turn.", NavigationSpeech.prompt(sign, 150.0, ""))
        assertEquals("In 150 meters, turn sharply left.", NavigationSpeech.prompt(-3, 150.0, ""))
    }
    @Test fun thaiDeviceLocaleAndRoadCannotChangeEnglishManeuver() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH"))
            assertEquals("en-US",NavigationSpeech.locale.toLanguageTag())
            assertEquals("In 150 meters, turn left.",NavigationSpeech.prompt(-2,150.0,"ถนนสุขุมวิท"))
            assertEquals("In 1.5 kilometers, turn right.",NavigationSpeech.prompt(2,1500.0,"Sukhumvit ถนนสุขุมวิท"))
        } finally { Locale.setDefault(old) }
    }
    @Test fun englishRoadAndArrivalRemainUnderstandable() {
        assertEquals("In 190 meters, turn left onto Oak Road.",NavigationSpeech.prompt(-2,190.0,"Oak Road"))
        assertEquals("Arriving at your destination.",NavigationSpeech.prompt(5,0.0,"",true))
        assertEquals("In 0 meters, continue straight.",NavigationSpeech.prompt(0,Double.NaN,""))
        assertFalse(NavigationSpeech.acceptsVoice("th")); assertFalse(NavigationSpeech.acceptsVoice("tha"))
        assertTrue(NavigationSpeech.acceptsVoice("en")); assertTrue(NavigationSpeech.acceptsVoice("eng"))
    }
}
