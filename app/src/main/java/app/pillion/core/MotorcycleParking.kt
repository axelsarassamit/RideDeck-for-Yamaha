package app.pillion.core

import kotlin.math.*

data class ParkingRecord(val id: String,val latitude: Double,val longitude: Double,val tags: Map<String,String>)
data class ParkingSuggestion(val record: ParkingRecord,val meters: Double,val dedicated: Boolean,val access: String,val fee: String) {
    val motorcycleConfirmed get()=dedicated || record.tags["motorcycle"] in setOf("yes","designated") || (record.tags["capacity:motorcycle"]?.toIntOrNull() ?: 0)>0
    val score get()=meters+(if(motorcycleConfirmed) 0 else 5000)+(if(dedicated) 0 else 200)+(when(access) { "yes","public","permissive","designated" -> 0; "customers" -> 150; else -> 75 })+(when(fee) { "no" -> 0; "yes" -> 50; else -> 25 })
}
object MotorcycleParking {
    fun suggestions(records: List<ParkingRecord>,lat: Double,lon: Double): List<ParkingSuggestion> {
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        val denied=setOf("no","private","permit","members","delivery","agricultural","forestry")
        return records.distinctBy { it.id }.mapNotNull { record ->
            if(record.latitude !in -90.0..90.0 || record.longitude !in -180.0..180.0) return@mapNotNull null
            val t=record.tags
            if(t["access"] in denied || t["motorcycle"] in denied || t.containsKey("motorcycle:conditional") || t.containsKey("access:conditional")) return@mapNotNull null
            val dedicated=t["amenity"]=="motorcycle_parking"
            if(!dedicated && t["amenity"]!="parking") return@mapNotNull null
            if(t["motor_vehicle"] in denied && t["motorcycle"] !in setOf("yes","designated")) return@mapNotNull null
            val dlat=Math.toRadians(record.latitude-lat);val dlon=Math.toRadians(record.longitude-lon)
            val a=sin(dlat/2).pow(2)+cos(Math.toRadians(lat))*cos(Math.toRadians(record.latitude))*sin(dlon/2).pow(2)
            val meters=6371000*2*asin(sqrt(a.coerceIn(0.0,1.0)))
            if(meters>1500) return@mapNotNull null
            ParkingSuggestion(record,meters,dedicated,t["access"].orEmpty(),t["fee:motorcycle"] ?: t["fee"].orEmpty())
        }.sortedWith(compareBy<ParkingSuggestion> { it.score }.thenBy { it.record.id }).take(3)
    }
}
