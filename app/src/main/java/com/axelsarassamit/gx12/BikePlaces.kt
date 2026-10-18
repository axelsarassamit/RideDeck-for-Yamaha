package com.axelsarassamit.gx12

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BikePlace(val name: String, val destination: String, val metres: Float = 0f, val source: String = "OpenStreetMap", val fuelPriceId: String = "", val category: String = "Unsorted", val id: String = "")
object BikePlaces {
    @Volatile private var stationTime = 0L
    @Volatile private var lastSearch = 0L
    @JvmField @Volatile var stations: List<BikePlace> = emptyList()
    fun freshStations(): List<BikePlace> = if (android.os.SystemClock.elapsedRealtime() - stationTime < 900000) stations else emptyList()
    val presetCategories = listOf("Unsorted", "Food & drink", "Shopping", "Fuel", "Service & repair", "Scenic stops", "Friends & family")
    @JvmStatic fun favorites(context: Context): List<BikePlace> = runCatching {
        val array = JSONArray(RidePreferences.prefs(context).getString("bike_favorites", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            val destination = item.getString("destination")
            BikePlace(item.getString("name"), destination,
                category = item.optString("category", "Unsorted").ifBlank { "Unsorted" },
                id = item.optString("id").ifBlank {
                    java.util.UUID.nameUUIDFromBytes("$index:$destination".toByteArray(Charsets.UTF_8)).toString()
                })
        }.getOrNull() }
    }.getOrDefault(emptyList())
    @JvmStatic fun categories(context: Context): List<String> {
        val custom = runCatching {
            val array = JSONArray(RidePreferences.prefs(context).getString("bike_favorite_categories", "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        return (presetCategories + custom + favorites(context).map { it.category }).distinct()
    }
    @JvmStatic fun addCategory(context: Context, value: String): String {
        val name = value.trim()
        require(name.isNotEmpty() && name.length <= 40) { "Use a category name of 1 to 40 characters" }
        val existing = categories(context).firstOrNull { it.equals(name, true) }
        if (existing != null) return existing
        val array = JSONArray()
        (categories(context).filterNot { it in presetCategories } + name).forEach(array::put)
        RidePreferences.prefs(context).edit().putString("bike_favorite_categories", array.toString()).apply()
        return name
    }
    @JvmStatic fun add(context: Context, name: String, destination: String) = add(context, name, destination, "Unsorted")
    @JvmStatic fun add(context: Context, name: String, destination: String, category: String) {
        require(name.isNotBlank() && destination.isNotBlank()) { "Enter a name and destination" }
        val places = favorites(context)
        require(places.size < 20) { "Up to 20 favorites are supported" }
        val group = addCategory(context, category)
        save(context, places + BikePlace(name.trim().take(120), destination, category=group, id=java.util.UUID.randomUUID().toString()))
    }
    @JvmStatic fun update(context: Context, id: String, name: String, category: String) {
        require(name.isNotBlank()) { "Enter a name" }
        val group = addCategory(context, category)
        save(context, favorites(context).map { if (it.id == id) it.copy(name=name.trim().take(120),category=group) else it })
    }
    @JvmStatic fun removeById(context: Context, id: String) = save(context, favorites(context).filterNot { it.id == id })
    @JvmStatic fun remove(context: Context, index: Int) = save(context, favorites(context).filterIndexed { i, _ -> i != index })
    private fun save(context: Context, places: List<BikePlace>) {
        val array = JSONArray()
        places.forEach { array.put(JSONObject().put("id",it.id).put("name", it.name).put("destination", it.destination).put("category",it.category)) }
        RidePreferences.prefs(context).edit().putString("bike_favorites", array.toString()).apply()
    }
    /** Wire layout recovered from CRC-verified StreetCross services 98 and 99. */
    fun listItem(index: Int, place: BikePlace, station: Boolean): ByteArray {
        var label = place.name.take(60)
        while (label.toByteArray(Charsets.UTF_8).size > 180) label = label.dropLast(1)
        val name = label.toByteArray(Charsets.UTF_8)
        val unit = if (station) "km".toByteArray() else ByteArray(0)
        return ByteBuffer.allocate(11 + unit.size + name.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(index.toShort()).putShort(0).put(1).put(name.size.toByte()).put(unit.size.toByte())
            .putFloat(if (station) place.metres / 1000 else 0f).put(unit).put(name).array()
    }
    @JvmStatic fun findStations(context: Context, latitude: Double, longitude: Double): Int {
        val now = android.os.SystemClock.elapsedRealtime()
        check(lastSearch == 0L || now - lastSearch >= 60000) { "Wait one minute before searching again" }
        lastSearch = now
        if(TomTomApi.configured(context)) {
            val found=runCatching { TomTomApi.stations(context,latitude,longitude) }.getOrNull()
            if(found!=null && found.isNotEmpty()) {
                stations=found;stationTime=android.os.SystemClock.elapsedRealtime()
                BikeDiagnostics.record(context,"Fuel search provider=TomTom results=${found.size}")
                return found.size
            }
        }
        stations = emptyList()
        val query = "[out:json][timeout:15];nwr[amenity=fuel](around:10000,$latitude,$longitude);out center tags;"
        val connection = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 25000
            connection.setRequestProperty("User-Agent", "RideDeck/Android personal-use fuel search")
            connection.doOutput = true
            connection.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            check(connection.responseCode == 200) { "Fuel search unavailable. Try again later." }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(response.length < 2000000) { "Fuel search response too large" }
            val elements = JSONObject(response).getJSONArray("elements")
            val found = mutableListOf<BikePlace>()
            for (i in 0 until elements.length()) {
                val item = elements.getJSONObject(i); val point = item.optJSONObject("center") ?: item
                if (!point.has("lat") || !point.has("lon")) continue
                val lat = point.getDouble("lat"); val lon = point.getDouble("lon"); val distance = FloatArray(1)
                Location.distanceBetween(latitude, longitude, lat, lon, distance)
                val tags = item.optJSONObject("tags") ?: JSONObject()
                found.add(BikePlace(tags.optString("name", tags.optString("brand", "Fuel station")), "$lat,$lon", distance[0]))
            }
            stations = found.sortedBy { it.metres }.take(20)
            stationTime = android.os.SystemClock.elapsedRealtime()
            BikeDiagnostics.record(context, "Fuel search completed results=${stations.size}")
            return stations.size
        } finally { connection.disconnect() }
    }
}
