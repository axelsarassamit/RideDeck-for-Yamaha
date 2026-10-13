package com.axelsarassamit.gx12
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.Locale

class FuelStationsActivity:RiderPage() {
    companion object { @JvmStatic fun open(a:Activity) { a.startActivity(Intent(a,FuelStationsActivity::class.java)) } }
    override fun onCreate(saved:Bundle?) { super.onCreate(saved);build("Nearby fuel stations");show() }
    private fun show() {
        rows.removeAllViews()
        state.text="Within 10 km. Distances are straight-line. Results also appear in the bike's Nearby Gas Stations menu."
        row(button("Find stations") { search() })
        for(place in BikePlaces.freshStations()) row(button(place.name+"\n"+String.format(Locale.US,"%.1f km · %s",place.metres/1000,place.source)) { detail(place) })
        row(text("Station search: TomTom when configured, otherwise OpenStreetMap contributors (ODbL). Fuel prices are separate from station search and require account access. Missing prices are never estimated."))
    }
    private fun search() {
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),96)
            state.text="Allow precise location, then Find stations again.";return
        }
        NativeNavigation.start(this)
        val fix=freshLocation() ?: run { state.text="Waiting for a recent GPS fix. Move outdoors, then Find stations again.";return }
        state.text="Finding nearby stations…"
        worker.execute {
            val result=runCatching { BikePlaces.findStations(applicationContext,fix.latitude,fix.longitude) }
            runOnUiThread { if(isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { show();state.text="$it stations ready for the phone and bike. Tap a station to choose a route." }
                    .onFailure { state.text=it.message ?: "Station search unavailable. Try again later." }
            }
        }
    }
    private fun detail(place:BikePlace) {
        val content=android.widget.LinearLayout(this).apply { orientation=android.widget.LinearLayout.VERTICAL }
        content.addView(text(String.format(Locale.US,"%.1f km away · %s",place.metres/1000,place.source)))
        val prices=text(if(place.fuelPriceId.isBlank()) "Price unavailable for this station."
            else if(!RidePreferences.prefs(this).getBoolean("tomtom_fuel_entitled",false)) "Price unavailable. TomTom Fuel Prices API needs separate account access." else "Loading reported prices…")
        content.addView(prices)
        val dialog=android.app.AlertDialog.Builder(this).setTitle(place.name).setView(android.widget.ScrollView(this).apply { addView(content) }).setNegativeButton("Back",null)
            .setPositiveButton("Choose route") { _,_->NavigationApi.coordinate(place.destination)?.let { RoutePreviewActivity.open(this,it.copy(label=place.name)) } }.show()
        for(which in listOf(android.app.AlertDialog.BUTTON_POSITIVE,android.app.AlertDialog.BUTTON_NEGATIVE)) dialog.getButton(which).apply { minHeight=dp(72);textSize=20f;isAllCaps=false }
        if(place.fuelPriceId.isNotBlank() && RidePreferences.prefs(this).getBoolean("tomtom_fuel_entitled",false)) worker.execute {
            val result=runCatching { TomTomApi.fuelPrices(applicationContext,place.fuelPriceId) }
            runOnUiThread { if(!dialog.isShowing || isDestroyed) return@runOnUiThread
                prices.text=result.getOrNull()?.takeIf { it.isNotEmpty() }?.joinToString("\n\n") {
                    "${it.grade}\n${String.format(Locale.US,"%.2f",it.value)} ${it.currency} / ${it.unit}\nReported: ${it.updated}\nSource: TomTom; confirm at the pump."
                } ?: "Price unavailable. Check Fuel Prices API access on your account."
            }
        }
    }
}
