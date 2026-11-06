package com.axelsarassamit.gx12

import android.app.Activity
import android.content.Intent
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import app.pillion.core.BikeMetric
import java.util.Locale

/** Live riding values, fuel range and bike notices. */
class BikeDataActivity : RiderPage() {
    companion object { @JvmStatic fun open(a: Activity) { a.startActivity(Intent(a, BikeDataActivity::class.java)) } }
    private val updates = android.os.Handler(android.os.Looper.getMainLooper())
    private val labels = linkedMapOf<BikeMetric, TextView>()
    private lateinit var history: TextView
    private lateinit var fuel: TextView
    private lateinit var notices: TextView
    override fun onCreate(saved: Bundle?) {
        RideAppearance.prepare(this); super.onCreate(saved); build("Bike data and fuel range")
        row(button("Connect to parked Yamaha") { choose() })
        row(button("Stop bike data") { BikeDataService.stop(this) })
        val auto = CheckBox(this).apply {
            text="Auto-connect bike data with the map";textSize=19f;minHeight=dp(72);setTextColor(RideStyle.TEXT)
            isChecked=YamahaBikeAutoConnect.enabled(this@BikeDataActivity)
            setOnCheckedChangeListener { _,checked->
                RidePreferences.prefs(this@BikeDataActivity).edit().putBoolean("bike_data_auto",checked).apply()
                YamahaBikeAutoConnect.changed(this@BikeDataActivity)
            }
        };row(auto)
        row(text("Uses the same paired CCU after the map connects. Stop bike data pauses automatic starts until the next map connection. Up to three attempts per map connection."))
        val continuous = CheckBox(this).apply {
            text="Keep bike data connected during a ride";textSize=19f;minHeight=dp(72);setTextColor(RideStyle.TEXT)
            isChecked=RidePreferences.prefs(this@BikeDataActivity).getBoolean("bike_data_during_ride",false)
            setOnCheckedChangeListener { _,checked->RidePreferences.prefs(this@BikeDataActivity).edit().putBoolean("bike_data_during_ride",checked).apply() }
        };row(continuous)
        row(button("Data refresh rate") {
            val prefs=RidePreferences.prefs(this);val rates=listOf(1000,250)
            android.app.AlertDialog.Builder(this).setTitle("Bike data refresh").setSingleChoiceItems(arrayOf("Normal · every second","Fast · every quarter second"),rates.indexOf(prefs.getInt("bike_data_interval",1000)).coerceAtLeast(0)) { dialog,choice->
                prefs.edit().putInt("bike_data_interval",rates[choice]).apply();dialog.dismiss()
                Toast.makeText(this,"Saved. Stop and reconnect bike data to apply.",Toast.LENGTH_LONG).show()
            }.setNegativeButton("Close",null).show()
        })
        for(pair in BikeMetric.entries.chunked(2)) {
            val line=LinearLayout(this)
            for(metric in pair) {
                val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8));background=RideStyle.row(this@BikeDataActivity) }
                card.addView(text(metric.label,16f));val value=text("Waiting",30f);labels[metric]=value;card.addView(value)
                line.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(6) })
            };row(line)
        }
        history=text("");row(history);fuel=text("");row(fuel);notices=text("");row(notices)
        row(button("Estimated fuel range and refills") { FuelRangeActivity.open(this) })
        row(button("Find a fuel station") { FuelStationsActivity.open(this) })
        row(button("Ride book") { RideBookActivity.open(this) })
        refresh()
    }
    private fun choose() {
        val required=if(android.os.Build.VERSION.SDK_INT>=31) arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_SCAN)
            else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val missing=required.filter { checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(),98);BikeDataStore.status("Allow bike discovery permission, then connect again.",false);return
        }
        if(BikeDataStore.state.active) { BikeDataStore.status("Stop the current bike-data connection before starting another.",BikeDataStore.state.authenticated);return }
        val devices=runCatching { getSystemService(BluetoothManager::class.java).adapter.bondedDevices.filter {
            it.name.orEmpty().contains("YCCU",true) || it.name.orEmpty().contains("Yamaha",true)
        } }.getOrDefault(emptyList())
        if(devices.isEmpty()) { BikeDataStore.status("No paired Yamaha CCU found. Turn Bluetooth on and check the existing pairing.",false);return }
        android.app.AlertDialog.Builder(this).setTitle("Select your parked Yamaha CCU").setItems(devices.map { it.name ?: "Yamaha CCU" }.toTypedArray()) { _,i->
            BikeDataService.start(this,devices[i].address,RidePreferences.prefs(this).getBoolean("bike_data_during_ride",false))
        }.setNegativeButton("Cancel",null).show()
    }
    private val tick=object:Runnable { override fun run() { refresh();updates.postDelayed(this,500) } }
    private fun refresh() {
        val s=BikeDataStore.state;val now=SystemClock.elapsedRealtime();state.text=s.status
        for((metric,label) in labels) {
            val value=s.values[metric];val fresh=s.active && value?.fresh(now)==true
            label.text=if(value==null) "Waiting" else String.format(Locale.US,if(metric==BikeMetric.RPM || metric==BikeMetric.SPEED) "%.0f %s" else "%.1f %s",value.value,metric.unit) + if(fresh) "" else "\nLast known"
            label.setTextColor(if(fresh) RideStyle.TEXT else RideStyle.MUTED)
        }
        history.text="${s.packets} bike-data packets received\n" + if(s.lastPacket==0L) "Waiting for bike data." else "Last packet ${(now-s.lastPacket)/1000} seconds ago."
        fuel.text="Use Estimated fuel range and refills for your manual full-tank estimate."
        notices.text=if(s.faults==null) "Diagnostic notices: waiting for the bike." else {
            (if(s.faults.isNullOrEmpty()) "No notices returned by the selected Yamaha diagnostic query." else s.faults.joinToString("\n") { "Yamaha notice ${it.code} · status ${it.status}" }) + "\nUpdated ${(now-s.faultsAt)/1000} seconds ago."
        }
    }
    override fun onResume() { super.onResume();updates.post(tick) }
    override fun onPause() { updates.removeCallbacksAndMessages(null);super.onPause() }
}
