package com.axelsarassamit.gx12

import android.content.Context
import android.os.SystemClock
import app.pillion.core.*
import org.json.JSONObject
import java.util.Locale

/** A manual model with a fresh authenticated odometer, separate from verified fuel telemetry. */
object FuelEstimateStore {
    private fun prefs(c:Context)=c.getSharedPreferences("manual_fuel_estimate",Context.MODE_PRIVATE)
    fun anchor(c:Context):ManualFuelAnchor?=runCatching {
        val j=JSONObject(prefs(c).getString("anchor",null)?:return null)
        ManualFuelAnchor(j.getString("bike"),j.getDouble("odo"),j.getDouble("tank"),j.getDouble("economy"),j.getDouble("buffer"),j.getDouble("partial"),j.getDouble("lastRefill"),j.getLong("at"),j.optBoolean("invalid"))
    }.getOrNull()
    fun save(c:Context,a:ManualFuelAnchor) {
        prefs(c).edit().putString("anchor",JSONObject().put("bike",a.bike).put("odo",a.fullOdometer).put("tank",a.tankLitres).put("economy",a.kmPerLitre).put("buffer",a.bufferLitres).put("partial",a.partialLitres).put("lastRefill",a.lastRefillOdometer).put("at",a.recordedAt).put("invalid",a.invalid).toString()).putString("last_odometer",a.lastRefillOdometer.toString()).apply()
    }
    fun clear(c:Context) { prefs(c).edit().clear().apply() }
    fun freshOdometer():Pair<String,Double>? {
        val s=BikeDataStore.state
        if(!s.active||!s.authenticated)return null
        val v=s.values[BikeMetric.ODOMETER]?:return null
        val bike=BikeDataService.selectedAddress()
        if(bike.isBlank()||!v.fresh(SystemClock.elapsedRealtime())||!v.value.isFinite())return null
        return bike to v.value
    }
    fun result(c:Context):ManualFuelResult? {
        val a=anchor(c)?:return null
        val (bike,odo)=freshOdometer()?:return null
        val last=prefs(c).getString("last_odometer",null)?.toDoubleOrNull()?:a.lastRefillOdometer
        val result=if(odo+0.05<last)null else ManualFuelEstimate.estimate(a,odo,bike)
        if(result!=null && odo!=last)prefs(c).edit().putString("last_odometer",odo.toString()).apply()
        if(result==null&&!a.invalid)save(c,a.copy(invalid=true))
        return result
    }
    @JvmStatic fun mapLabel(c:Context):String?=result(c)?.let {
        "Est. fuel range ~${(it.refuelKm/10).toInt()*10} km"
    }
    fun details(c:Context):String {
        val a=anchor(c)?:return "Fuel range not set. Confirm a full tank while connected to your parked Yamaha."
        val r=result(c)
        if(r==null)return if(a.invalid||anchor(c)?.invalid==true) "Fuel estimate needs a new full-tank confirmation: bike or odometer changed." else "Fuel estimate paused. Connect bike data for a fresh odometer. Full-tank record is saved."
        return "Est. fuel range ~${(r.refuelKm/10).toInt()*10} km\nFull tank: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(a.recordedAt))+String.format(Locale.US,"\n%.0f km since full tank · %.1f km/L\n%.1f L estimated · %.1f L refill buffer",r.distanceKm,a.kmPerLitre,r.remainingLitres,a.bufferLitres)
    }
}
