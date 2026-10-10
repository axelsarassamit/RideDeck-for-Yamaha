package app.pillion.core

import java.net.URI
import java.net.URLDecoder

/** Only explicit destinations are accepted; a Google Maps @camera is never a target. */
object SharedMapLink {
    fun allowed(link: String): Boolean = runCatching {
        val uri = URI(link)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (host == "maps.app.goo.gl" || (host == "goo.gl" && uri.path.orEmpty().startsWith("/maps")) ||
                (Regex("(?:www\\.|maps\\.)?google\\.(?:com|[a-z]{2}|com\\.[a-z]{2}|co\\.[a-z]{2})").matches(host) &&
                    (host.startsWith("maps.") || uri.path.orEmpty().startsWith("/maps"))))
    }.getOrDefault(false)

    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
    fun destination(link: String): String? {
        require(allowed(link)) { "Unsupported map link" }
        val uri = URI(link)
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) decode(parts[0]) to decode(parts[1]) else null
        }.toMap()
        // Directions query is authoritative, even when another place appears in the URL.
        for (key in listOf("destination", "query", "q")) {
            query[key]?.trim()?.removePrefix("loc:")?.takeIf { it.isNotBlank() && !it.contains("://") && !it.startsWith("place_id:") }?.let {
                validateCoordinate(it)
                return it.take(500)
            }
        }
        val decoded = decode(uri.rawPath.orEmpty())
        Regex("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)").findAll(decoded).lastOrNull()?.let {
            return coordinate(it.groupValues[1], it.groupValues[2])
        }
        // /dir/origin/destination: ignore origins and camera positions.
        val parts = decoded.split('/')
        val dir = parts.indexOf("dir")
        if (dir >= 0) {
            val target = parts.drop(dir + 1).takeWhile { !it.startsWith('@') && !it.startsWith("data=") }.lastOrNull { it.isNotBlank() }
            target?.let { validateCoordinate(it); return it.take(500) }
        }
        Regex("/maps/(?:place|search)/([^/]+)").find(decoded)?.groupValues?.get(1)?.let {
            validateCoordinate(it)
            return it.take(500)
        }
        return null
    }
    private fun coordinate(lat: String, lon: String): String {
        require(lat.toDouble() in -90.0..90.0 && lon.toDouble() in -180.0..180.0)
        return "$lat,$lon"
    }
    private fun validateCoordinate(text: String) {
        Regex("^(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(text)?.let {
            coordinate(it.groupValues[1], it.groupValues[2])
        }
    }
}
