package app.pillion.core

import java.text.Normalizer

object DestinationInput {
    fun normalize(text: String): String = Normalizer.normalize(text.take(500),Normalizer.Form.NFC)
        .replace(Regex("\\bpu\\s+chao\\b",RegexOption.IGNORE_CASE),"Puchao")
        .replace(Regex("\\bmueng\\b",RegexOption.IGNORE_CASE),"Mueang")
        .replace(Regex("\\brd\\.?(?=\\s|,|$)",RegexOption.IGNORE_CASE),"Road")
        .replace(Regex("\\s+")," ").replace(Regex("\\s*,\\s*"),", ").trim()
    fun queries(text: String): List<String> {
        val normalized=normalize(text)
        val name=normalized.substringBefore(',').trim()
        return listOf(normalized,name).filter { it.length>=3 }.distinct().take(2)
    }
    fun coordinates(raw: String): Pair<Double,Double>? {
        val text=raw.trim().removePrefix("loc:")
        val match=Regex("^(-?\\d+(?:\\.\\d+)?)\\s*[,;\\s]\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(text) ?: return null
        val lat=match.groupValues[1].toDouble();val lon=match.groupValues[2].toDouble()
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Latitude must be -90 to 90; longitude -180 to 180." }
        return lat to lon
    }
}
