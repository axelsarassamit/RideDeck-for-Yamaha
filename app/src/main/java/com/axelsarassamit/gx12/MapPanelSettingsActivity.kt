package com.axelsarassamit.gx12

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

/** The same flat settings page is embedded in Setup and opened from navigation shortcuts. */
class MapPanelSettingsActivity : RiderPage() {
    companion object { @JvmStatic fun open(a: Activity) { a.startActivity(Intent(a, MapPanelSettingsActivity::class.java)) } }
    override fun onCreate(saved: Bundle?) {
        RideAppearance.prepare(this); super.onCreate(saved); build("Bike display & maps")
        state.visibility=View.GONE
        row(MapPanelSettings.create(this))
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==74)MapPanelSettings.refreshPermission(this)
    }
}

object MapPanelSettings {
    @JvmStatic fun refreshPermission(a:Activity) {
        a.window.decorView.findViewWithTag<TextView>("precise_location_status")?.text="Precise location: "+
            if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
    }
    @JvmStatic fun create(a: Activity): LinearLayout {
        val prefs=RidePreferences.prefs(a)
        fun dp(n:Int)=(n*a.resources.displayMetrics.density).toInt()
        fun column()=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        val page=column()
        fun words(parent:LinearLayout,label:String,size:Float=15f,muted:Boolean=true)=TextView(a).apply {
            text=label;textSize=size;setTextColor(if(muted) RideStyle.MUTED else RideStyle.TEXT)
            setPadding(dp(8),dp(8),dp(8),dp(8));parent.addView(this)
        }
        fun heading(label:String) { words(page,label,19f,false).apply { setPadding(dp(8),dp(24),dp(8),dp(8));androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) } }
        fun action(parent:LinearLayout,label:String,run:()->Unit)=Button(a).apply {
            text=label;isAllCaps=false;textSize=17f;minHeight=dp(56);gravity=Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(RideStyle.TEXT);background=RideStyle.row(a);backgroundTintList=null;stateListAnimator=null;elevation=0f
            setPadding(dp(16),dp(12),dp(16),dp(12));setOnClickListener { run() }
            parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        fun toggle(parent:LinearLayout,key:String,label:String,default:Boolean,detail:String?=null):Switch {
            val choice=Switch(a).apply {
                text=label;textSize=17f;minHeight=dp(60);setTextColor(RideStyle.TEXT);setPadding(dp(8),dp(8),dp(8),dp(8))
                isChecked=prefs.getBoolean(key,default);tag=key
                setOnCheckedChangeListener { _,checked->prefs.edit().putBoolean(key,checked).apply() }
            };parent.addView(choice,LinearLayout.LayoutParams(-1,-2));if(detail!=null)words(parent,detail);return choice
        }
        words(page,"Changes are saved here. Review routing changes before starting the next route.")
        heading("Panels")
        words(page,"Phone panel",17f,false)
        val modes=arrayOf("map","arrows","music")
        val panel=Spinner(a).apply {
            contentDescription="Phone panel";minimumHeight=dp(56);tag="phone_panel"
            adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,arrayOf("Map","Turn arrows","Music"))
            setSelection(modes.indexOf(prefs.getString("phone_panel","map")).coerceAtLeast(0))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:AdapterView<*>?)=Unit
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                    if(prefs.getString("phone_panel","map")!=modes[position])prefs.edit().putString("phone_panel",modes[position]).apply()
                }
            }
        };page.addView(panel,LinearLayout.LayoutParams(-1,-2))
        words(page,"Bike view: choose Default, Turn-by-turn or Turn list using Navigation > Change view on the bike. Phone panel choices do not change the bike's view.")
        action(page,"Preview bike map") { NavigationUi.bikePreview(a) }
        heading("Guidance")
        toggle(page,"navigation_voice","Voice guidance",true,"English navigation announcements")
        action(page,"Test English voice") { NavigationVoice.sample(a) }
        toggle(page,"route_lanes","Lane arrows",true,"Shown only when the route provider supplies lane data.")
        heading("Routing & traffic")
        toggle(page,"tomtom_routing","Use TomTom routing",true,"Requires a configured TomTom account. Without it, routes use Valhalla without live traffic.")
        toggle(page,"tomtom_refresh","Refresh traffic route every 5 minutes",true)
        toggle(page,"tomtom_incident_alerts","Accident and road-closure alerts",true,"TomTom reports ahead of your route; coverage and account access apply.")
        fun roadSummary():String {
            val road=RoutePreferencesUi.read(a)
            val avoids=listOfNotNull("motorways".takeIf{road.avoidMotorways},"tolls".takeIf{road.avoidTolls},"ferries".takeIf{road.avoidFerries},"unpaved roads".takeIf{road.avoidDirt})
            return "Road preferences\n"+(if(avoids.isEmpty()) "No road avoidances" else "Avoid "+avoids.joinToString(", "))
        }
        lateinit var roadButton:Button
        roadButton=action(page,roadSummary()) { RoutePreferencesUi.show(a) { roadButton.text=roadSummary() } }
        words(page,"Precise location: "+if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed").tag="precise_location_status"
        action(page,"Review location permission") {
            if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)Toast.makeText(a,"Precise location is allowed",Toast.LENGTH_SHORT).show()
            else a.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),74)
        }
        heading("Ride tools")
        // The planner is contributed by the parallel feature release; discover only a registered activity.
        val funRide=Intent().setClassName(a,"com.axelsarassamit.gx12.FunRideActivity")
        if(runCatching { a.packageManager.getActivityInfo(funRide.component!!,0) }.isSuccess) action(page,"Go for a fun ride") { a.startActivity(funRide) }
        action(page,"Nearby fuel stations") { FuelStationsActivity.open(a) }
        action(page,"Traffic nearby") { TrafficActivity.open(a) }
        action(page,"Ride book") { RideBookActivity.open(a) }
        action(page,"Bike data and fuel range") { BikeDataActivity.open(a) }
        action(page,"Estimated fuel range") { FuelRangeActivity.open(a) }
        action(page,"Stop navigation") { a.startService(Intent(a,NativeNavigationService::class.java).setAction("STOP")) }
        heading("Advanced")
        val advanced=column().apply { visibility=View.GONE }
        lateinit var disclosure:Button
        disclosure=action(page,"Map & routing accounts · Show") {
            val open=advanced.visibility!=View.VISIBLE;advanced.visibility=if(open) View.VISIBLE else View.GONE
            disclosure.text="Map & routing accounts · "+if(open) "Hide" else "Show"
        };page.addView(advanced)
        val mapStatus=words(advanced,"MapTiler: "+if(NavigationSecrets.read(a,"maptiler").isBlank()) "Not configured" else "Configured")
        val tomtomStatus=words(advanced,"TomTom: "+if(NavigationSecrets.read(a,"tomtom").isBlank()) "Not configured" else "Configured")
        words(advanced,"Requests send search or location to the selected provider. Usage allowances and account access apply. Keys are encrypted on this phone; blank fields keep existing keys.")
        fun keyField(label:String)=EditText(a).apply {
            hint=label;contentDescription=label;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled=false;setTextColor(RideStyle.TEXT);setHintTextColor(RideStyle.MUTED);minHeight=dp(56);advanced.addView(this)
        }
        val map=keyField("New MapTiler key");val tomtom=keyField("New TomTom key")
        toggle(advanced,"tomtom_fuel_entitled","Fuel Prices API access",false,"Enable only if your account includes this separate service.")
        action(advanced,"Save account keys") {
            try {
                if(map.text.isNotBlank())NavigationSecrets.save(a,"maptiler",map.text.toString().trim())
                if(tomtom.text.isNotBlank())NavigationSecrets.save(a,"tomtom",tomtom.text.toString().trim())
                mapStatus.text="MapTiler: "+if(NavigationSecrets.read(a,"maptiler").isBlank()) "Not configured" else "Configured"
                tomtomStatus.text="TomTom: "+if(NavigationSecrets.read(a,"tomtom").isBlank()) "Not configured" else "Configured"
                map.text.clear();tomtom.text.clear();Toast.makeText(a,"Saved on this phone",Toast.LENGTH_SHORT).show()
            } catch(_:Exception) { map.error="Could not store keys on this phone" }
        }
        action(advanced,"MapTiler website") { a.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://cloud.maptiler.com/"))) }
        return page
    }
}
