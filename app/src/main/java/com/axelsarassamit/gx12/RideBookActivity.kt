package com.axelsarassamit.gx12
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.Locale

class RideBookActivity:RiderPage() {
    companion object { @JvmStatic fun open(a:Activity) { a.startActivity(Intent(a,RideBookActivity::class.java)) } }
    private var telemetry:RideTelemetry?=null
    private var allTime=false
    private fun date(t:Long)=java.text.SimpleDateFormat("dd MMM yyyy HH:mm:ss",Locale.getDefault()).format(java.util.Date(t))
    private fun peak(name:String,value:Double)=when(name) {
        "speed"->String.format(Locale.US,"Maximum speed · %.1f km/h",value)
        "lean"->String.format(Locale.US,"Maximum phone lean estimate · %.1f°",value)
        "accel"->String.format(Locale.US,"Peak GPS acceleration · %.2f m/s² (%.2f g)",value,value/9.80665)
        else->String.format(Locale.US,"Peak GPS deceleration · %.2f m/s² (%.2f g)",value,value/9.80665)
    }
    override fun onCreate(saved:Bundle?) { super.onCreate(saved);build("Ride book");refresh() }
    private fun refresh() {
        state.text="Phone GPS and mounted-phone estimates are recorded separately from Yamaha CCU readings. Connect Bike data before starting a ride to include source-decoded engine readings and raw bike fields."
        rows.removeAllViews()
        row(button(if(allTime) "Show ride history" else "Show all-time records") { allTime=!allTime;refresh() })
        row(button("Bike data and fuel range") { BikeDataActivity.open(this) })
        row(button("Refresh ride data") { refresh() })
        row(button(if(DashRideService.active()) "Pause ride" else "Start / resume ride") {
            if(DashRideService.active()) DashRideService.pause(this) else {
                if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),97)
                    state.text="Allow precise location, then tap Start / resume ride again.";return@button
                };DashRideService.start(this)
            };android.os.Handler(mainLooper).postDelayed({ refresh() },400)
        })
        row(button("End ride") { android.app.AlertDialog.Builder(this).setTitle("Finish this ride?").setNegativeButton("Keep ride",null)
            .setPositiveButton("End ride") { _,_->DashRideService.end(this);android.os.Handler(mainLooper).postDelayed({ refresh() },400) }.show() })
        row(button("Calibrate lean while parked upright") {
            android.app.AlertDialog.Builder(this).setTitle("Phone fixed to bike?").setMessage("Park safely and hold the bike upright. The phone must be rigidly mounted, facing the rider, with its top aligned along the bike. Recalibrate after moving the mount. This is a phone estimate, not Yamaha IMU data.")
                .setNegativeButton("Cancel",null).setPositiveButton("Calibrate now") { _,_->
                    state.text=if(telemetry?.calibrate()==true) "Upright position saved. Lean is logged only with a fresh GPS speed above 10.8 km/h." else "No reliable rotation reading. Keep the phone upright in its mount and try again; flat mounts are unsupported."
                }.show()
        })
        val resultRows=android.widget.LinearLayout(this).apply { orientation=android.widget.LinearLayout.VERTICAL };row(resultRows)
        RideBookStore.records(applicationContext) { records->
            if(isFinishing || isDestroyed || resultRows.parent==null) return@records
            if(records.isEmpty()) resultRows.addView(text("No rides recorded yet. Start a ride with precise location enabled. Samples stay on this phone."))
            else if(allTime) {
                for(column in listOf("speed","accel","decel","lean")) {
                    val winner=records.filter { it.peaks.containsKey(column) }.maxByOrNull { it.peaks.getValue(column).first }
                    val value=winner?.peaks?.get(column)
                    resultRows.addView(text(if(value==null) "${column.replaceFirstChar { it.uppercase() }} · No trusted measurement yet" else peak(column,value.first)+"\n"+date(value.second)+" · Ride ${winner.id}"))
                }
            } else for(ride in records) {
                resultRows.addView(button("Ride ${ride.id} · ${ride.state}\n${date(ride.started)}\n${ride.seconds/60} min · ${String.format(Locale.US,"%.2f km",ride.meters/1000)}") {
                    val detail=(ride.peaks.entries.map { peak(it.key,it.value.first)+"\n"+date(it.value.second) } + ride.bike.entries.map { (name,value)->
                        val label=runCatching { app.pillion.core.BikeMetric.valueOf(name).label }.getOrDefault(name)
                        "Maximum $label · ${String.format(Locale.US,"%.1f",value.maximum)} ${value.unit}\n${date(value.maximumAt)}\n${value.source}\nMinimum ${String.format(Locale.US,"%.1f",value.minimum)} ${value.unit} · ${date(value.minimumAt)}"
                    }).joinToString("\n\n").ifBlank { "No measurements recorded. GPS or the bike connection may have been unavailable." }
                    android.app.AlertDialog.Builder(this).setTitle("Ride ${ride.id}").setMessage(detail).setPositiveButton("Close",null).show()
                })
            }
            if(allTime) for(metric in app.pillion.core.BikeMetric.entries) {
                val winner=records.mapNotNull { ride->ride.bike[metric.name]?.let { ride to it } }.maxByOrNull { it.second.maximum }
                if(winner!=null) resultRows.addView(text("Maximum ${metric.label} · ${String.format(Locale.US,"%.1f",winner.second.maximum)} ${metric.unit}\n${date(winner.second.maximumAt)} · Ride ${winner.first.id}\n${winner.second.source}"))
            }
        }
        row(text("GPS samples with poor accuracy, missing speed accuracy, gaps or implausible acceleration are excluded. Deceleration is not brake force. Lean depends on the mounted phone. CCU values use the observed XMAX layout and need a dashboard comparison. Unknown brake/indicator fields are retained as raw bike data, never labeled as decoded controls. All records stay on this phone."))
    }
    override fun onResume() { super.onResume();telemetry=RideTelemetry(this).also { it.sensors() } }
    override fun onPause() { telemetry?.stop();telemetry=null;super.onPause() }
}
