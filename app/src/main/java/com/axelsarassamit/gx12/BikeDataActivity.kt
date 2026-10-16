package com.axelsarassamit.gx12

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import app.pillion.core.BikeMetric
import java.io.File
import java.util.Locale

/** Large riding values with a separate, explicitly labeled parked diagnostic section. */
class BikeDataActivity : RiderPage() {
    companion object { @JvmStatic fun open(a: Activity) { a.startActivity(Intent(a, BikeDataActivity::class.java)) } }
    private val updates = android.os.Handler(android.os.Looper.getMainLooper())
    private val labels = linkedMapOf<BikeMetric, TextView>()
    private lateinit var history: TextView
    private lateinit var fuel: TextView
    private lateinit var notices: TextView
    private lateinit var raw: TextView
    private var exporting: File? = null
    override fun onCreate(saved: Bundle?) {
        RideAppearance.prepare(this); super.onCreate(saved); build("Bike data and fuel range")
        row(button("Connect to parked Yamaha") { choose() })
        row(button("Stop bike data") { BikeDataService.stop(this) })
        row(button("Read bike snapshot") { BikeDataService.readSnapshot() })
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
        row(button("Find a fuel station") { FuelStationsActivity.open(this) })
        row(button("Ride book") { RideBookActivity.open(this) })
        row(text("Close Y-Connect and disconnect nRF Connect before testing RideDeck independently. Keep existing pairing. These readings use the source-confirmed XMAX layout and still need a dashboard check. Fuel-counter units, brake and indicator meanings remain unverified. Raw byte changes are not decoded controls."))
        row(button("Mark a parked test action") {
            android.app.AlertDialog.Builder(this).setTitle("Record what you can see").setItems(arrayOf("Baseline: brakes / indicators off","Front brake held","Front brake released","Rear brake held","Rear brake released","Left indicator on","Left indicator off","Right indicator on","Right indicator off","Hazards on","Hazards off")) { _,i->
                val event=listOf("baseline","front_on","front_off","rear_on","rear_off","left_on","left_off","right_on","right_off","hazards_on","hazards_off")[i]
                Toast.makeText(this,if(BikeDataStore.mark(this,event)) "Your observation was recorded." else "Connect bike data first.",Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel",null).show()
        })
        raw=text("");raw.visibility=android.view.View.GONE;row(raw)
        row(button("Show / hide raw diagnostic fields") { raw.visibility=if(raw.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE;refresh() })
        row(button("Save private test log") {
            exporting=BikeDataStore.export(this)
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE,"yamaha-private-test-${System.currentTimeMillis()}.jsonl"),99)
        })
        refresh()
    }
    private fun choose() {
        if(android.os.Build.VERSION.SDK_INT>=31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),98);BikeDataStore.status("Allow Nearby devices, then connect again.",false);return
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
        fuel.text="Fuel remaining and range: awaiting confirmed litres and consumption units.\n" +
            if(s.injection==null) "Fuel-injection counter: waiting for a supported reply." else "Fuel-injection counter: ${s.injection} raw units.\nThis is not remaining fuel. Received ${(now-s.injectionAt)/1000} seconds ago."
        notices.text=if(s.faults==null) "Diagnostic notices: waiting for the bike." else {
            (if(s.faults.isNullOrEmpty()) "No notices returned by the selected Yamaha diagnostic query." else s.faults.joinToString("\n") { "Yamaha notice ${it.code} · status ${it.status}" }) + "\nUpdated ${(now-s.faultsAt)/1000} seconds ago."
        }
        if(raw.visibility==android.view.View.VISIBLE) raw.text=s.raw.entries.joinToString("\n\n") { "${it.key}\nRaw ${it.value.first}\nReceived ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it.value.second))}" }.ifBlank { "No permitted raw fields received yet." }
    }
    override fun onResume() { super.onResume();updates.post(tick) }
    override fun onPause() { updates.removeCallbacksAndMessages(null);super.onPause() }
    @Deprecated("Android activity result compatibility") override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request!=99) return
        val file=exporting;exporting=null
        val success=result==RESULT_OK && file!=null && data?.data!=null && runCatching {
            contentResolver.openOutputStream(data.data!!)?.use { out->file.inputStream().use { it.copyTo(out) } } ?: error("No destination")
        }.isSuccess
        file?.delete();Toast.makeText(this,if(success) "Private test log saved." else "Log was not exported; the phone still retains the local data.",Toast.LENGTH_LONG).show()
    }
}
