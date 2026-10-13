package com.axelsarassamit.gx12

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

object NavigationUi {
    private val worker=Executors.newFixedThreadPool(2)
    private val main=Handler(Looper.getMainLooper())
    private val names=arrayOf("Map", "Turn arrows", "Music")
    private val modes=arrayOf("map","arrows","music")
    @JvmStatic fun configure(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Navigation and panels").setItems(arrayOf("Map and routing accounts", "Phone panel", "Bike display", "Precise location", "Voice guidance", "Stop navigation", "Nearby fuel stations", "Traffic nearby", "Ride book", "Bike data and fuel range")) { _, choice ->
            when(choice) {
                0 -> credentials(activity)
                1 -> {
                    val key="phone_panel"
                    val selected=modes.indexOf(RidePreferences.prefs(activity).getString(key,"map")).coerceAtLeast(0)
                    AlertDialog.Builder(activity).setTitle(if(choice==1)"Phone panel" else "Bike display")
                        .setSingleChoiceItems(names,selected) { dialog,index ->
                            RidePreferences.prefs(activity).edit().putString(key,modes[index]).apply(); dialog.dismiss()
                            Toast.makeText(activity,if(choice==1)"Saved. Return to the cockpit to see this panel." else "Bike display updated",Toast.LENGTH_LONG).show()
                        }.setNegativeButton("Close",null).show()
                }
                2 -> AlertDialog.Builder(activity).setTitle("Bike display")
                    .setMessage("Select Default view, Turn-by-turn or Turn list from Navigation > Change view on the bike. RideDeck sends map images and native turn guidance. Music controls are available in the Phone panel; the bike's built-in player uses its Yamaha connection.")
                    .setNeutralButton("Preview bike map") { _,_ -> bikePreview(activity) }
                    .setPositiveButton("Close",null).show()
                3 -> permission(activity,true)
                4 -> voiceGuidance(activity)
                5 -> activity.startService(Intent(activity,NativeNavigationService::class.java).setAction("STOP"))
                6 -> FuelStationsActivity.open(activity)
                7 -> TrafficActivity.open(activity)
                8 -> RideBookActivity.open(activity)
                9 -> BikeDataActivity.open(activity)
            }
        }.setNegativeButton("Close",null).show()
    }
    private fun bikePreview(activity: Activity) {
        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Bike map preview" }
        val dialog=AlertDialog.Builder(activity).setTitle("Bike map preview").setView(image).setPositiveButton("Close",null).create()
        val refresh=object: Runnable { override fun run() {
            if(!dialog.isShowing) return
            NativeNavigation.frame("map")?.let { image.setImageBitmap(BitmapFactory.decodeByteArray(it,0,it.size)) }
            main.postDelayed(this,750)
        } }
        dialog.setOnDismissListener { main.removeCallbacks(refresh) }
        dialog.setOnShowListener { main.post(refresh) }
        dialog.show()
    }
    private fun permission(activity: Activity, notifyGranted: Boolean = false): Boolean {
        if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            if(notifyGranted) Toast.makeText(activity,"Precise location is allowed",Toast.LENGTH_SHORT).show(); return true
        }
        activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),74)
        Toast.makeText(activity,"Allow precise location, then tap Map again",Toast.LENGTH_LONG).show()
        return false
    }
    private fun credentials(activity: Activity) {
        val fields=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,0) }
        val map=EditText(activity).apply { hint="MapTiler key (blank keeps existing key)"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        fields.addView(map)
        val tomtom=EditText(activity).apply { hint="TomTom key (blank keeps existing key)";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        fields.addView(tomtom)
        val prefs=RidePreferences.prefs(activity)
        val routing=CheckBox(activity).apply { text="TomTom motorcycle routes with traffic";isChecked=prefs.getBoolean("tomtom_routing",true) };fields.addView(routing)
        val refresh=CheckBox(activity).apply { text="Refresh traffic route every 5 minutes";isChecked=prefs.getBoolean("tomtom_refresh",true) };fields.addView(refresh)
        val fuel=CheckBox(activity).apply { text="My account has separate Fuel Prices API access";isChecked=prefs.getBoolean("tomtom_fuel_entitled",false) };fields.addView(fuel)
        AlertDialog.Builder(activity).setTitle("Map and routing accounts")
            .setMessage("MapTiler draws the map. Optional TomTom improves place search, nearby stations and traffic-aware motorcycle routes. Without TomTom, Valhalla uses a public fair-use server without live traffic. Requests send your search or location to the selected provider. TomTom has usage allowances; automatic refresh uses routing requests. Fuel Prices requires separate access and is excluded from the free plan. Keys are encrypted on this phone.")
            .setView(fields).setNegativeButton("Close",null).setNeutralButton("MapTiler website") { _,_ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://cloud.maptiler.com/")))
            }.setPositiveButton("Save",null).create().also { dialog ->
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        if(map.text.isNotBlank()) NavigationSecrets.save(activity,"maptiler",map.text.toString().trim())
                        if(tomtom.text.isNotBlank()) NavigationSecrets.save(activity,"tomtom",tomtom.text.toString().trim())
                        prefs.edit().putBoolean("tomtom_routing",routing.isChecked).putBoolean("tomtom_refresh",refresh.isChecked).putBoolean("tomtom_fuel_entitled",fuel.isChecked).apply()
                        dialog.dismiss(); Toast.makeText(activity,"Saved on this phone",Toast.LENGTH_SHORT).show()
                    } catch(_: Exception) { map.error="Could not store keys on this phone" }
                } }; dialog.show()
            }
    }
    @JvmStatic fun search(activity: Activity, initial: String?) {
        activity.startActivity(Intent(activity,DestinationSearchActivity::class.java).putExtra("query",initial))
    }
    @JvmStatic fun mapMenu(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Map")
            .setItems(arrayOf("Search destination", "Stop route", "Voice guidance")) { _, choice ->
                when (choice) {
                    0 -> search(activity, null)
                    1 -> NativeNavigation.stopRoute()
                    2 -> voiceGuidance(activity)
                }
            }.setNegativeButton("Close", null).show()
    }
    private fun voiceGuidance(activity: Activity) {
        val prefs=RidePreferences.prefs(activity)
        val dialog=AlertDialog.Builder(activity).setTitle("Navigation voice: English")
            .setSingleChoiceItems(arrayOf("On","Off"),if(prefs.getBoolean("navigation_voice",true)) 0 else 1) { _,index ->
                prefs.edit().putBoolean("navigation_voice",index == 0).apply()
            }.setNeutralButton("Test English voice",null).setNegativeButton("Close",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { NavigationVoice.sample(activity) } }
        dialog.show()
    }
    @JvmStatic fun panel(activity: Activity): View {
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xff151c17.toInt()) }
        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Map; tap for navigation menu"; setOnClickListener { mapMenu(activity) } }
        val state=TextView(activity).apply {
            setTextColor(0xfff4f6fa.toInt()); gravity=Gravity.CENTER_VERTICAL; textSize=11f; setPadding(8,4,8,4)
            val logo = activity.getDrawable(R.drawable.maptiler_logo)!!
            val density = activity.resources.displayMetrics.density
            logo.setBounds(0, 0, (48 * density).toInt(), (16 * density).toInt())
            setCompoundDrawables(logo, null, null, null); compoundDrawablePadding=(6*density).toInt(); contentDescription="Map credits"
            setOnClickListener {
                val credit=TextView(activity).apply {
                    text=android.text.Html.fromHtml("© <a href='https://www.maptiler.com/copyright/'>MapTiler</a> © <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap contributors</a><br>Routing: TomTom when configured, otherwise Valhalla (FOSSGIS)<br>Search: TomTom, MapTiler or Android geocoder",android.text.Html.FROM_HTML_MODE_LEGACY)
                    movementMethod=android.text.method.LinkMovementMethod.getInstance(); setPadding(24,20,24,20)
                }
                AlertDialog.Builder(activity).setTitle("Map credits").setView(credit).setPositiveButton("Close",null).show()
            }
        }
        val mapArea = FrameLayout(activity)
        mapArea.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ -> NativeNavigation.phoneViewport(right - left, bottom - top) }
        mapArea.addView(image, FrameLayout.LayoutParams(-1, -1))
        val zoom = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        listOf("+", "−").forEachIndexed { index, label ->
            zoom.addView(Button(activity).apply {
                text = label; textSize = 30f; isAllCaps = false
                contentDescription = if (index == 0) "Zoom in" else "Zoom out"
                setOnClickListener { NativeNavigation.zoom(index == 0) }
            }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin=dp(8) })
        }
        mapArea.addView(zoom, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(8)
        })
        page.addView(mapArea, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(state)
        val refresh=object: Runnable { override fun run() {
            if(!page.isAttachedToWindow) return
            image.setImageBitmap(NativeNavigation.phoneBitmap(RidePreferences.prefs(activity).getString("phone_panel","map") ?: "map"))
            state.text="© MapTiler  © OpenStreetMap contributors"
            main.postDelayed(this,750)
        } }
        page.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { main.post(refresh) }
            override fun onViewDetachedFromWindow(v: View) { main.removeCallbacks(refresh); NativeNavigation.phoneViewport(0, 0) }
        })
        return page
    }
}
