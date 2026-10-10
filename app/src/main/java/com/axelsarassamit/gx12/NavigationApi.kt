package com.axelsarassamit.gx12

import app.pillion.core.RouteTimeLeg
import app.pillion.core.RoadSpeedLimits
import app.pillion.core.SpeedLimitEdge
import app.pillion.core.SpeedLimitWindow
import app.pillion.core.SpeedLimitPoint
import app.pillion.core.SolarMapTheme
import android.content.Context
import android.location.Location
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL

data class NavigationPlace(val label: String, val latitude: Double, val longitude: Double)
data class NavigationTurn(val text: String, val sign: Int, val start: Int, val end: Int, val road: String = "", val millis: Long = 0)
data class NavigationRoute(val destination: NavigationPlace, val points: List<LatLng>, val turns: List<NavigationTurn>, val meters: Double, val millis: Long) {
    val cumulative = DoubleArray(points.size).also { distances ->
        val result = FloatArray(1)
        for (i in 1 until points.size) {
            Location.distanceBetween(points[i-1].latitude, points[i-1].longitude, points[i].latitude, points[i].longitude, result)
            distances[i] = distances[i-1] + result[0]
        }
    }
    val timeLegs = turns.map { RouteTimeLeg(cumulative[it.start],cumulative[it.end],it.millis) }
}

object NavigationApi {
    fun englishMapStyle(context: Context, dark: Boolean = false): String {
        val key = NavigationSecrets.read(context, "maptiler")
        check(key.isNotBlank()) { "Add your MapTiler key in Setup." }
        val style = request(Uri.parse("https://api.maptiler.com/maps/${SolarMapTheme.styleId(dark)}/style.json?key=${Uri.encode(key)}"))
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
    private fun request(uri: Uri, provider: String = "MapTiler", body: JSONObject? = null): JSONObject {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "RideDeck-Yamaha")
            if (provider == "Valhalla") connection.setRequestProperty("X-Client-Id", "RideDeck-for-Yamaha")
            if(body != null) {
                connection.requestMethod="POST"; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json")
                val bytes=body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
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
                val safeMessage = (if(apiKey.isBlank()) message else message.replace(apiKey, "[hidden]"))
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

    fun speedLimits(route: NavigationRoute, start: Int, end: Int): SpeedLimitWindow {
        require(start >= 0 && end > start && end <= route.points.lastIndex && end-start <= 400)
        val requested=route.points.subList(start,end+1)
        val shape=JSONArray().apply { requested.forEach { put(JSONObject().put("lat",it.latitude).put("lon",it.longitude)) } }
        val payload=JSONObject().put("shape",shape).put("costing","motorcycle").put("shape_match","edge_walk").put("units","kilometers")
            .put("filters",JSONObject().put("action","include").put("attributes",JSONArray(listOf("shape","edge.begin_shape_index","edge.end_shape_index","edge.speed_limit"))))
        val response=request(Uri.parse("https://valhalla1.openstreetmap.de/trace_attributes"),"Valhalla",payload)
        require(response.optString("units") == "kilometers") { "Speed-limit units unavailable." }
        val matched=decodePolyline(response.getString("shape"))
        val edges=response.getJSONArray("edges")
        val limits=RoadSpeedLimits.segments(matched.size,(0 until edges.length()).map { index ->
            val item=edges.getJSONObject(index)
            SpeedLimitEdge(item.optInt("begin_shape_index",-1),item.optInt("end_shape_index",-1),(item.opt("speed_limit") as? Number)?.toDouble())
        })
        // Repeated boundary points are allowed; changed road geometry is rejected.
        val aligned=RoadSpeedLimits.align(requested.map { SpeedLimitPoint(it.latitude,it.longitude) },matched.map { SpeedLimitPoint(it.latitude,it.longitude) },limits)
        return SpeedLimitWindow(start,aligned)
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
            .appendQueryParameter("key", key).appendQueryParameter("language", "en").appendQueryParameter("autocomplete", "true").appendQueryParameter("limit", "10").appendQueryParameter("types", "poi,address,road,place,locality,municipality,region,country")
        location?.let { url.appendQueryParameter("proximity", "${it.longitude},${it.latitude}") }
        val features = request(url.build()).getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val item = features.getJSONObject(i); val center = item.optJSONArray("center") ?: return@mapNotNull null
            val lon = center.getDouble(0); val lat = center.getDouble(1)
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            NavigationPlace(item.optString("place_name", item.optString("text", "Destination")), lat, lon)
        }
    }
    fun route(context: Context, origin: Location, target: NavigationPlace): NavigationRoute = routes(origin, target, false).first()

    fun routes(origin: Location, target: NavigationPlace, alternatives: Boolean = true): List<NavigationRoute> {
        val locations = JSONArray()
            .put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
            .put(JSONObject().put("lat", target.latitude).put("lon", target.longitude))
        val payload = JSONObject().put("locations", locations).put("costing", "motorcycle")
            .put("units", "kilometers").put("language", "en-US").put("alternates", if(alternatives) 2 else 0)
        val response = request(Uri.parse("https://valhalla1.openstreetmap.de/route"), "Valhalla", payload)
        return parseRoutes(response, target)
    }

    internal fun parseRoutes(response: JSONObject, target: NavigationPlace): List<NavigationRoute> {
        val routes = mutableListOf(parseTrip(response.getJSONObject("trip"), target))
        val alternatives = response.optJSONArray("alternates")
        for (i in 0 until minOf(alternatives?.length() ?: 0, 2)) {
            val alternate = runCatching { parseTrip(alternatives!!.getJSONObject(i).getJSONObject("trip"), target) }.getOrNull() ?: continue
            if(routes.none { it.points == alternate.points }) routes.add(alternate)
        }
        return routes
    }

    private fun parseTrip(trip: JSONObject, target: NavigationPlace): NavigationRoute {
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
                item.optJSONArray("street_names")?.optString(0).orEmpty(),
                item.optDouble("time",0.0).takeIf { it.isFinite() && it >= 0 }?.let { (it*1000).toLong() } ?: 0
            )
        }
        val summary = trip.getJSONObject("summary")
        val seconds=summary.getDouble("time")
        require(seconds.isFinite() && seconds >= 0 && seconds <= Long.MAX_VALUE/1000.0) { "Route duration unavailable." }
        val meters = summary.getDouble("length") * 1000.0
        require(meters.isFinite() && meters >= 0) { "Route distance unavailable." }
        return NavigationRoute(target, points, turns, meters, (seconds*1000).toLong())
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
