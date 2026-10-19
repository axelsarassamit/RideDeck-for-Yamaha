package com.axelsarassamit.gx12

import android.content.Context
import app.pillion.core.NavigationResumeTarget

/** Private on-phone resume state. Explicit Stop clears the relevant session. */
object RideBackgroundSession {
    private fun prefs(c: Context)=c.getSharedPreferences("ride_background",Context.MODE_PRIVATE)
    fun navigationActive(c: Context)=prefs(c).getBoolean("navigation_active",false)
    fun navigationStarted(c: Context) { prefs(c).edit().putBoolean("navigation_active",true).commit() }
    fun saveDestination(c: Context,target: NavigationPlace,stops:List<NavigationPlace> = emptyList(),style:String = "relaxed") {
        prefs(c).edit().putString("label",target.label.take(300)).putString("latitude",target.latitude.toString()).putString("longitude",target.longitude.toString())
            .putString("itinerary",ItineraryRoutes.encode(stops)).putString("ride_style",style).commit()
    }
    fun saveItinerary(c:Context,stops:List<NavigationPlace>,style:String) {
        prefs(c).edit().putString("itinerary",ItineraryRoutes.encode(stops)).putString("ride_style",style).commit()
    }
    fun itinerary(c:Context)=ItineraryRoutes.decode(prefs(c).getString("itinerary",null))
    fun rideStyle(c:Context)=prefs(c).getString("ride_style","relaxed") ?: "relaxed"
    fun clearDestination(c: Context) { prefs(c).edit().remove("itinerary").remove("ride_style").remove("label").remove("latitude").remove("longitude").commit() }
    fun destination(c: Context): NavigationPlace? {
        val p=prefs(c)
        val saved=NavigationResumeTarget.restore(navigationActive(c),p.getString("label",null),p.getString("latitude",null),p.getString("longitude",null)) ?: return null
        return NavigationPlace(saved.label,saved.latitude,saved.longitude)
    }
    fun navigationStopped(c: Context) { prefs(c).edit().putBoolean("navigation_active",false).remove("itinerary").remove("ride_style").remove("label").remove("latitude").remove("longitude").commit() }
    fun castStarted(c: Context,address: String,automatic: Boolean,session: String) {
        prefs(c).edit().putBoolean("cast_active",true).putString("cast_address",address).putBoolean("cast_automatic",automatic).putString("cast_session",session).commit()
    }
    fun castAddress(c: Context)=if(prefs(c).getBoolean("cast_active",false)) prefs(c).getString("cast_address",null) else null
    fun castAutomatic(c: Context)=prefs(c).getBoolean("cast_automatic",false)
    fun castSession(c: Context)=prefs(c).getString("cast_session","restored") ?: "restored"
    fun castStopped(c: Context) { prefs(c).edit().putBoolean("cast_active",false).remove("cast_address").remove("cast_session").remove("cast_automatic").commit() }
}
