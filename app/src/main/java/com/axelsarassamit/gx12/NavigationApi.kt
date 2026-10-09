package com.axelsarassamit.gx12

import android.content.Context
import android.location.Location
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL

data class NavigationPlace(val label: String, val latitude: Double, val longitude: Double)
data class NavigationTurn(val text: String, val sign: Int, val start: Int, val end: Int, val road: String = "")
data class NavigationRoute(val destination: NavigationPlace, val points: List<LatLng>, val turns: List<NavigationTurn>, val meters: Double, val millis: Long) {
    val cumulative = DoubleArray(points.size).also { distances ->
        val result = FloatArray(1)
        for (i in 1 until points.size) {
            Location.distanceBetween(points[i-1].latitude, points[i-1].longitude, points[i].latitude, points[i].longitude, result)
            distances[i] = distances[i-1] + result[0]
        }
    }
}

object NavigationApi {
    fun englishMapStyle(context: Context): String {
        val key = NavigationSecrets.read(context, "maptiler")
        check(key.isNotBlank()) { "Add your MapTiler key in Setup." }
        val style = request(Uri.parse("https://api.maptiler.com/maps/streets-v4/style.json?key=${Uri.encode(key)}"))
        val layers = style.getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val layout = layers.getJSONObject(i).optJSONObject("layout") ?: continue
            val field = layout.opt("text-field") ?: continue
            // Planet v4 supplies localized names as name:xx. Keep shields and numeric labels unchanged.
            if (Regex("name(?::[A-Za-z_-]+)?").containsMatchIn(field.toString())) {
                layout.put("text-field", org.json.JSONArray("[\"coalesce\",[\"get\",\"name:en\"],[\"get\",\"name:latin\"],[\"get\",\"name\"]]"))
            }
        }
        return style.toString()
    }
    private fun request(uri: Uri, provider: String = "MapTiler"): JSONObject {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "RideDeck-Yamaha")
            if (provider == "Valhalla") connection.setRequestProperty("X-Client-Id", "RideDeck-for-Yamaha")
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val apiKey = uri.getQueryParameter("key").orEmpty()
                val message = runCatching {
                    val bytes = connection.errorStream?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (output.size() < 8192) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 8192 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: return@runCatching ""
                    JSONObject(String(bytes, Charsets.UTF_8)).optString("message")
                }.getOrDefault("")
                val safeMessage = message
                    .replace(apiKey, "[hidden]")
                    .replace(Regex("-?\\d{1,3}\\.\\d+,\\s*-?\\d{1,3}\\.\\d+"), "[location]")
                    .replace(Regex("[\\r\\n\\t]+"), " ")
                    .take(180)
                val detail = if (safeMessage.isBlank()) "" else ": $safeMessage"
                throw IllegalStateException("$provider rejected the request (HTTP $responseCode)$detail")
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    check(output.size() + count <= 4 * 1024 * 1024) { "Provider response is too large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    fun coordinate(text: String): NavigationPlace? {
        val match = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").matchEntire(text) ?: return null
        val lat = match.groupValues[1].toDouble(); val lon = match.groupValues[2].toDouble()
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Coordinates are outside valid ranges." }
        return NavigationPlace("Shared destination", lat, lon)
    }
    fun search(context: Context, query: String, location: Location?): List<NavigationPlace> {
        coordinate(query)?.let { return listOf(it) }
        val key = NavigationSecrets.read(context, "maptiler")
        check(key.isNotBlank()) { "Add your MapTiler key in Setup." }
        val url = Uri.Builder().scheme("https").authority("api.maptiler.com").appendPath("geocoding").appendPath(query.take(300) + ".json")
            .appendQueryParameter("key", key).appendQueryParameter("language", "en").appendQueryParameter("autocomplete", "true").appendQueryParameter("limit", "6")
        location?.let { url.appendQueryParameter("proximity", "${it.longitude},${it.latitude}") }
        val features = request(url.build()).getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val item = features.getJSONObject(i); val center = item.optJSONArray("center") ?: return@mapNotNull null
            val lon = center.getDouble(0); val lat = center.getDouble(1)
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            NavigationPlace(item.optString("place_name", item.optString("text", "Destination")), lat, lon)
        }
    }
    fun route(context: Context, origin: Location, target: NavigationPlace): NavigationRoute {
        val locations = JSONArray()
            .put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
            .put(JSONObject().put("lat", target.latitude).put("lon", target.longitude))
        val payload = JSONObject().put("locations", locations).put("costing", "motorcycle")
            .put("units", "kilometers").put("language", "en-US")
        val uri = Uri.parse("https://valhalla1.openstreetmap.de/route").buildUpon()
            .appendQueryParameter("json", payload.toString()).build()
        val trip = request(uri, "Valhalla").getJSONObject("trip")
        check(trip.optInt("status", 0) == 0) { trip.optString("status_message", "No motorcycle route was found.") }
        val legs = trip.getJSONArray("legs")
        require(legs.length() == 1) { "Unexpected route response." }
        val leg = legs.getJSONObject(0)
        val points = decodePolyline(leg.getString("shape"))
        require(points.size in 2..100000) { "Route geometry unavailable." }
        val raw = leg.getJSONArray("maneuvers")
        require(raw.length() in 1..65535) { "Route instruction list is unavailable or too large." }
        val turns = (0 until raw.length()).map { i ->
            val item = raw.getJSONObject(i)
            NavigationTurn(
                item.optString("instruction", "Continue"), valhallaTurnSign(item.optInt("type")),
                item.optInt("begin_shape_index").coerceIn(points.indices), item.optInt("end_shape_index").coerceIn(points.indices),
                item.optJSONArray("street_names")?.optString(0).orEmpty()
            )
        }
        val summary = trip.getJSONObject("summary")
        return NavigationRoute(target, points, turns, summary.getDouble("length") * 1000.0, (summary.getDouble("time") * 1000).toLong())
    }

    private fun valhallaTurnSign(type: Int): Int = when (type) {
        9 -> 1; 10, 18, 20, 23 -> 2; 11, 12 -> 3
        14, 13 -> -3; 15, 19, 21, 24 -> -2; 16 -> -1
        4, 5, 6 -> 5; 26, 27 -> 6
        else -> 0
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        require(encoded.length <= 2_000_000) { "Route geometry is too large." }
        val points = ArrayList<LatLng>()
        var index = 0; var latitude = 0; var longitude = 0
        fun component(): Int {
            var result = 0; var shift = 0; var chunk: Int
            do {
                require(index < encoded.length && shift <= 30) { "Invalid route geometry." }
                chunk = encoded[index++].code - 63
                require(chunk in 0..63) { "Invalid route geometry." }
                result = result or ((chunk and 0x1f) shl shift)
                shift += 5
            } while (chunk >= 0x20)
            return if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        }
        while (index < encoded.length) {
            latitude += component(); longitude += component()
            val lat = latitude / 1_000_000.0; val lon = longitude / 1_000_000.0
            require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid route geometry." }
            points.add(LatLng(lat, lon))
            require(points.size <= 100000) { "Route geometry is too large." }
        }
        return points
    }
}
