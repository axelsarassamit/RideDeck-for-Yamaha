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
        AlertDialog.Builder(activity).setTitle("Navigation and panels").setItems(arrayOf("Map and routing accounts", "Phone panel", "Bike display", "Precise location", "Voice guidance", "Stop navigation")) { _, choice ->
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
                    .setPositiveButton("Close",null).show()
                3 -> permission(activity,true)
                4 -> {
                    val prefs=RidePreferences.prefs(activity)
                    AlertDialog.Builder(activity).setTitle("Voice guidance").setSingleChoiceItems(arrayOf("On","Off"),if(prefs.getBoolean("navigation_voice",true))0 else 1) { d,i -> prefs.edit().putBoolean("navigation_voice",i==0).apply(); d.dismiss() }.setNegativeButton("Close",null).show()
                }
                5 -> activity.startService(Intent(activity,NativeNavigationService::class.java).setAction("STOP"))
            }
        }.setNegativeButton("Close",null).show()
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
        AlertDialog.Builder(activity).setTitle("Map and routing accounts")
            .setMessage("MapTiler provides map data and searches for shops, places and addresses. Valhalla calculates motorcycle routes using the public FOSSGIS demo server. Route start and destination are sent to that server. It needs no API key and follows fair-use limits, with no availability guarantee.")
            .setView(fields).setNegativeButton("Close",null).setNeutralButton("MapTiler website") { _,_ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://cloud.maptiler.com/")))
            }.setPositiveButton("Save",null).create().also { dialog ->
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        if(map.text.isNotBlank()) NavigationSecrets.save(activity,"maptiler",map.text.toString().trim())
                        dialog.dismiss(); Toast.makeText(activity,"Saved on this phone",Toast.LENGTH_SHORT).show()
                    } catch(_: Exception) { map.error="Could not store keys on this phone" }
                } }; dialog.show()
            }
    }
    @JvmStatic fun search(activity: Activity, initial: String?) {
        if(!permission(activity)) return
        NativeNavigation.start(activity)
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(20,8,20,8) }
        val field=EditText(activity).apply { hint="Shops, cafés, places or addresses"; textSize=22f; setSingleLine(true) }
        val status=TextView(activity)
        val results=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        page.addView(field); page.addView(status); page.addView(ScrollView(activity).apply { addView(results) },LinearLayout.LayoutParams(-1,420))
        val dialog=AlertDialog.Builder(activity).setTitle("Destination").setView(page).setNegativeButton("Close",null).setNeutralButton("Stop route") { _,_ -> NativeNavigation.stopRoute() }.create()
        var generation=0
        var pending: Runnable?=null
        field.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                val query=s?.toString()?.trim().orEmpty(); val request=++generation
                pending?.let(main::removeCallbacks); results.removeAllViews()
                if(query.length<3) { status.text="Enter at least three characters"; return }
                status.text="Searching..."
                pending=Runnable {
                    worker.execute {
                        val result=runCatching { NavigationApi.search(activity.applicationContext,query,NativeNavigation.location) }
                        main.post {
                            if(request!=generation || !dialog.isShowing || activity.isDestroyed) return@post
                            result.onSuccess { places ->
                                status.text=if(places.isEmpty())"No matches. Try a place name and city, or a full address." else "Choose the destination"
                                places.forEach { place ->
                                    results.addView(Button(activity).apply {
                                        text=place.label; isAllCaps=false; textSize=20f; minHeight=(80*activity.resources.displayMetrics.density).toInt()
                                        setOnClickListener {
                                            AlertDialog.Builder(activity).setTitle(place.label).setMessage("Compare routes on the map before starting navigation.")
                                                .setNegativeButton("Cancel",null).setNeutralButton("Save place") { _,_ ->
                                                    AlertDialog.Builder(activity).setTitle("Save place").setItems(arrayOf("Favorite","Home","Work")) { _,slot ->
                                                        runCatching {
                                                            val coordinate="${place.latitude},${place.longitude}"
                                                            if(slot==0) BikePlaces.add(activity,place.label.take(80),coordinate)
                                                            else RidePreferences.prefs(activity).edit().putString(if(slot==1) "bike_home" else "bike_work",coordinate).apply()
                                                        }.onSuccess { Toast.makeText(activity,"${arrayOf("Favorite","Home","Work")[slot]} saved",Toast.LENGTH_SHORT).show() }
                                                            .onFailure { status.text="Place could not be saved" }
                                                    }.setNegativeButton("Cancel",null).show()
                                                }.setPositiveButton("Choose route") { _,_ ->
                                                    dialog.dismiss(); RoutePreviewActivity.open(activity,place)
                                                }.show()
                                        }
                                    })
                                }
                            }.onFailure { status.text="Search unavailable. Check MapTiler key, internet and account quota in Setup."; BikeDiagnostics.record(activity,"Native search failed exception=${it.javaClass.simpleName}") }
                        }
                    }
                }.also { main.postDelayed(it,600) }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        dialog.setOnDismissListener { generation++; pending?.let(main::removeCallbacks) }
        dialog.show(); initial?.let { field.setText(it) }
    }
    @JvmStatic fun mapMenu(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Map")
            .setItems(arrayOf("Search destination", "Stop route", "Voice guidance")) { _, choice ->
                when (choice) {
                    0 -> search(activity, null)
                    1 -> NativeNavigation.stopRoute()
                    2 -> {
                        val prefs = RidePreferences.prefs(activity)
                        AlertDialog.Builder(activity).setTitle("Voice guidance")
                            .setSingleChoiceItems(arrayOf("On", "Off"), if (prefs.getBoolean("navigation_voice", true)) 0 else 1) { dialog, selected ->
                                prefs.edit().putBoolean("navigation_voice", selected == 0).apply()
                                dialog.dismiss()
                            }.setNegativeButton("Close", null).show()
                    }
                }
            }.setNegativeButton("Close", null).show()
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
                    text=android.text.Html.fromHtml("© <a href='https://www.maptiler.com/copyright/'>MapTiler</a> © <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap contributors</a><br>Routing: Valhalla (FOSSGIS)<br>Search results: MapTiler",android.text.Html.FROM_HTML_MODE_LEGACY)
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
