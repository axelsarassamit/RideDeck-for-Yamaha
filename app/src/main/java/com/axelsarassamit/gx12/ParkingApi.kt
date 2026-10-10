package com.axelsarassamit.gx12

import app.pillion.core.MotorcycleParking
import app.pillion.core.ParkingRecord
import app.pillion.core.ParkingSuggestion
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ParkingApi {
    private data class Cached(val at: Long,val suggestions: List<ParkingSuggestion>)
    private val cache=LinkedHashMap<String,Cached>()
    fun near(target: NavigationPlace): List<ParkingSuggestion> {
        require(target.latitude in -90.0..90.0 && target.longitude in -180.0..180.0)
        val key="${target.latitude},${target.longitude}"
        synchronized(cache) { cache[key]?.takeIf { android.os.SystemClock.elapsedRealtime()-it.at<300000 }?.let { return it.suggestions } }
        val around="(around:1500,${target.latitude},${target.longitude})"
        val query="[out:json][timeout:12];(nwr$around[amenity=motorcycle_parking];nwr$around[amenity=parking];);out center tags;"
        val connection=URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.connectTimeout=8000;connection.readTimeout=16000
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("User-Agent","RideDeck-for-Yamaha")
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            val body=("data="+URLEncoder.encode(query,"UTF-8")).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size);connection.outputStream.use { it.write(body) }
            check(connection.responseCode==200) { "Parking search unavailable HTTP ${connection.responseCode}" }
            val bytes=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer);if(count<0) break
                    check(output.size()+count<=1024*1024) { "Parking response too large" };output.write(buffer,0,count)
                };output.toByteArray()
            }
            val response=JSONObject(String(bytes,Charsets.UTF_8))
            check(!response.has("remark")) { "Parking search incomplete. Retry." }
            val elements=response.getJSONArray("elements")
            val records=(0 until elements.length()).mapNotNull { i ->
                val item=elements.getJSONObject(i);val position=item.optJSONObject("center") ?: item
                val tags=item.optJSONObject("tags") ?: return@mapNotNull null
                ParkingRecord(item.optString("type")+":"+item.getLong("id"),position.optDouble("lat",Double.NaN),position.optDouble("lon",Double.NaN),tags.keys().asSequence().associateWith { tags.optString(it) })
            }
            val suggestions=MotorcycleParking.suggestions(records,target.latitude,target.longitude)
            synchronized(cache) {
                if(cache.size>=16) cache.remove(cache.keys.first())
                cache[key]=Cached(android.os.SystemClock.elapsedRealtime(),suggestions)
            }
            return suggestions
        } finally { connection.disconnect() }
    }
}
