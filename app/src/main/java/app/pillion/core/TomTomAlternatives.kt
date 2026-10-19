package app.pillion.core

/** TomTom rejects alternativeType when maxAlternatives is zero. */
object TomTomAlternatives {
    fun parameters(enabled:Boolean):Map<String,String> = if(enabled)
        mapOf("maxAlternatives" to "2", "alternativeType" to "anyRoute")
    else mapOf("maxAlternatives" to "0")
}
