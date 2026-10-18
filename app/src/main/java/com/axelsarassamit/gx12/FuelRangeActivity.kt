package com.axelsarassamit.gx12

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import app.pillion.core.*

/** Parked-only explicit refill entry, no automatic tank-level claims. */
class FuelRangeActivity:RiderPage() {
    companion object { @JvmStatic fun open(a:Activity) { a.startActivity(Intent(a,FuelRangeActivity::class.java)) } }
    private val updates=android.os.Handler(android.os.Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() { state.text=FuelEstimateStore.details(this@FuelRangeActivity);updates.postDelayed(this,2000) } }
    override fun onCreate(saved:Bundle?) {
        RideAppearance.prepare(this);super.onCreate(saved);build("Estimated fuel range")
        row(text("Set up while parked. This is a refill estimate, not the bike's fuel gauge. A fresh connected-bike odometer is required. Your measured consumption is required; no factory average is assumed."))
        row(button("Confirm full tank / set up") { setup() })
        row(button("Record fuel added") { refill() })
        row(button("Find a fuel station") { FuelStationsActivity.open(this) })
        row(text("Thailand XMAX 2024 nominal tank: 13 L. Fill normally according to the owner's manual. The buffer is your chosen refuelling allowance, not Yamaha's reserve threshold. Traffic, riding style and fill differences affect this estimate. Unrecorded refills make it inaccurate."))
        row(button("Clear fuel estimate") {
            android.app.AlertDialog.Builder(this).setTitle("Clear fuel records?").setMessage("Removes this estimate and its refill baseline.")
                .setPositiveButton("Clear") { _,_->FuelEstimateStore.clear(this) }.setNegativeButton("Cancel",null).show()
        })
    }
    override fun onResume() { super.onResume();updates.post(tick) }
    override fun onPause() { updates.removeCallbacks(tick);super.onPause() }
    private fun edit(label:String,value:String)=EditText(this).apply {
        hint=label;setText(value);textSize=22f;minHeight=dp(72);setTextColor(RideStyle.TEXT);setHintTextColor(RideStyle.TEXT)
        inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun connected():Pair<String,Double>? {
        val s=BikeDataStore.state.values[BikeMetric.SPEED]
        if(s==null||!s.fresh(android.os.SystemClock.elapsedRealtime())||s.value>0.5) {
            Toast.makeText(this,"Park the bike and wait for fresh speed and odometer readings.",Toast.LENGTH_LONG).show();return null
        }
        return FuelEstimateStore.freshOdometer().also { if(it==null)Toast.makeText(this,"Connect Yamaha bike data and wait for a fresh odometer.",Toast.LENGTH_LONG).show() }
    }
    private fun setup() {
        if(connected()==null)return
        val old=FuelEstimateStore.anchor(this)
        val tank=edit("Tank capacity (L)",old?.tankLitres?.toString()?:"13.0")
        val economy=edit("Measured average (km/L)",old?.kmPerLitre?.toString()?:"")
        val buffer=edit("Your refill buffer (L)",old?.bufferLitres?.toString()?:"")
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;addView(text("Confirm only immediately after filling the tank. Enter your measured average km/L and desired buffer, which may be zero."));addView(text("Tank capacity (L)"));addView(tank);addView(text("Measured average (km/L)"));addView(economy);addView(text("Refill buffer (L)"));addView(buffer) }
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Tank is full now").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("Confirm full tank",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val (bike,odo)=connected()?:return@setOnClickListener
            val t=tank.text.toString().toDoubleOrNull();val e=economy.text.toString().toDoubleOrNull();val b=buffer.text.toString().toDoubleOrNull()
            if(t==null||e==null||b==null) { Toast.makeText(this,"Enter all three numbers.",Toast.LENGTH_LONG).show();return@setOnClickListener }
            val a=ManualFuelAnchor(bike,odo,t,e,b,recordedAt=System.currentTimeMillis())
            if(!ManualFuelEstimate.valid(a)) { Toast.makeText(this,"Check capacity, measured km/L and buffer. Buffer must be below tank capacity.",Toast.LENGTH_LONG).show();return@setOnClickListener }
            FuelEstimateStore.save(this,a);dialog.dismiss();state.text=FuelEstimateStore.details(this)
        } };dialog.show()
    }
    private fun refill() {
        if(connected()==null)return
        val a=FuelEstimateStore.anchor(this)
        if(a==null||a.invalid) { Toast.makeText(this,"Set a full-tank baseline first.",Toast.LENGTH_LONG).show();return }
        val litres=edit("Actual litres added","")
        val full=CheckBox(this).apply { text="Tank is full after this refill";textSize=20f;minHeight=dp(72) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;addView(text("Enter the pump's actual litres. Full fills update measured km/L using distance and all partial fills since the previous full tank."));addView(litres);addView(full) }
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Record fuel added").setView(box).setPositiveButton("Save refill",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val (bike,odo)=connected()?:return@setOnClickListener
            val current=FuelEstimateStore.anchor(this)?:return@setOnClickListener
            val amount=litres.text.toString().toDoubleOrNull()
            if(bike!=current.bike||amount==null) { Toast.makeText(this,"Check the connected bike and litres added.",Toast.LENGTH_LONG).show();return@setOnClickListener }
            val next=if(full.isChecked)ManualFuelEstimate.nextFull(current,odo,amount,System.currentTimeMillis()) else ManualFuelEstimate.partial(current,odo,amount)
            if(next==null) { Toast.makeText(this,"Refill does not fit this baseline. Check litres, odometer and tank capacity. Full-fill learning needs at least 10 km.",Toast.LENGTH_LONG).show();return@setOnClickListener }
            FuelEstimateStore.save(this,next);dialog.dismiss();state.text=FuelEstimateStore.details(this)
        } };dialog.show()
    }
}
