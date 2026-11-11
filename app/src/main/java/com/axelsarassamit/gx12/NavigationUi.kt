package com.axelsarassamit.gx12

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object NavigationUi {
    private val worker=Executors.newFixedThreadPool(2)
    private val main=Handler(Looper.getMainLooper())
    /** Always visible on the riding cockpit; these are actions, not setup menus. */
    @JvmStatic fun quickActions(activity:Activity):LinearLayout {
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        val prefs = RidePreferences.prefs(activity)
        val showFunRide = prefs.getBoolean("quick_action_fun_ride", true)
        val showTools = prefs.getBoolean("quick_action_tools", true)
        return LinearLayout(activity).apply {
            tag="ride_quick_actions"
            val actions=mutableListOf<Pair<String,()->Unit>>("Search" to { search(activity,null) })
            if (showFunRide) actions.add("Fun ride" to { activity.startActivity(Intent(activity,FunRideActivity::class.java)) })
            if (showTools) actions.add("Ride tools" to { RideToolsActivity.open(activity) })
            for((label,action) in actions) addView(Button(activity).apply {
                text=label;contentDescription="Ride action: $label";RideStyle.action(this,false);maxLines=2
                setPadding(dp(3),dp(2),dp(3),dp(2));setOnClickListener{action()}
            },LinearLayout.LayoutParams(0,dp(64),1f).apply{leftMargin=dp(3);rightMargin=dp(3)})
        }
    }
    @JvmStatic fun configure(activity: Activity) { MapPanelSettingsActivity.open(activity) }
    internal fun bikePreview(activity: Activity) {
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
    @JvmStatic fun search(activity: Activity, initial: String?) {
        activity.startActivity(Intent(activity,DestinationSearchActivity::class.java).putExtra("query",initial))
    }
    /** One map action menu, shared with the cockpit's Ride tools button. */
    @JvmStatic fun mapMenu(activity: Activity) { RideToolsActivity.open(activity) }
    @JvmStatic fun panel(activity: Activity): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(RideStyle.BACKGROUND) }
        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Map; tap for navigation menu"; setOnClickListener { mapMenu(activity) } }
        val state=TextView(activity).apply {
            setTextColor(RideStyle.TEXT); gravity=Gravity.CENTER_VERTICAL; textSize=11f; setPadding(8,4,8,4)
            val logo = activity.getDrawable(R.drawable.maptiler_logo)!!
            logo.setBounds(0, 0, (48 * density).toInt(), (16 * density).toInt())
            setCompoundDrawables(logo, null, null, null); compoundDrawablePadding=(6*density).toInt(); contentDescription="Map credits"
            setOnClickListener {
                val credit=TextView(activity).apply {
                    text= Html.fromHtml("© <a href='https://www.maptiler.com/copyright/'>MapTiler</a> © <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap contributors</a><br>Routing: TomTom when configured, otherwise Valhalla (FOSSGIS)<br>Search: TomTom, MapTiler or Android geocoder",
                        Html.FROM_HTML_MODE_LEGACY)
                    movementMethod= LinkMovementMethod.getInstance(); setPadding(24,20,24,20)
                }
                AlertDialog.Builder(activity).setTitle("Map credits").setView(credit).setPositiveButton("Close",null).show()
            }
        }
        val directions=TextView(activity).apply {
            textSize=22f;maxLines=2;ellipsize= TextUtils.TruncateAt.END;setTextColor(RideStyle.TEXT)
            setPadding(12,6,12,6);setBackgroundColor(RideStyle.PANEL);contentDescription="Next navigation instruction"
        }
        page.addView(directions,LinearLayout.LayoutParams(-1,-2))
        val body=LinearLayout(activity)
        val mapArea=FrameLayout(activity)
        mapArea.addOnLayoutChangeListener { _,left,top,right,bottom,_,_,_,_ -> NativeNavigation.phoneViewport(right-left,bottom-top) }
        mapArea.addView(image,FrameLayout.LayoutParams(-1,-1))
        val empty = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(RideStyle.BACKGROUND)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            val brandView = VelocityBrandView(activity)
            val logoSize = dp(100)
            addView(brandView, LinearLayout.LayoutParams(logoSize, logoSize).apply { bottomMargin = dp(12) })
            addView(TextView(activity).apply {
                text = "RideDeck for Yamaha"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(RideStyle.TEXT)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
            addView(TextView(activity).apply {
                text = "Ready for your ride\nTap below to start navigation"
                textSize = 14f
                setTextColor(RideStyle.MUTED)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(16) })
            addView(Button(activity).apply {
                text = "Start Navigation"
                contentDescription = "Start navigation and open map"
                RideStyle.action(this, true)
                setOnClickListener {
                    NativeNavigation.start(activity)
                }
            }, LinearLayout.LayoutParams(dp(210), dp(56)))
        }
        mapArea.addView(empty,FrameLayout.LayoutParams(-1,-1))

        val tripHud = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackground(RideStyle.surface(activity, RideStyle.PANEL, 8, true))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val tripEtaView = TextView(activity).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(RideStyle.TEXT)
        }
        val tripFuelView = TextView(activity).apply {
            textSize = 12f
            setTextColor(RideStyle.MUTED)
        }
        tripHud.addView(tripEtaView)
        tripHud.addView(tripFuelView)
        mapArea.addView(tripHud, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(8); bottomMargin = dp(8)
        })

        body.addView(mapArea,LinearLayout.LayoutParams(0,-1,1f))
        val zoom=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setBackgroundColor(RideStyle.PANEL) }
        listOf("+","−").forEachIndexed { index,label ->
            zoom.addView(Button(activity).apply {
                RideStyle.action(this,false);text=label;textSize=30f;setPadding(0,0,0,0)
                contentDescription=if(index==0) "Zoom in" else "Zoom out"
                setOnClickListener { NativeNavigation.zoom(index==0) }
            },LinearLayout.LayoutParams(dp(64),dp(64)))
        }
        body.addView(zoom,LinearLayout.LayoutParams(dp(64),-1))
        page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val gps=TextView(activity).apply { textSize=13f;setTextColor(RideStyle.MUTED);setPadding(12,2,12,2) }
        page.addView(gps)
        page.addView(state)
        val refresh=object: Runnable { override fun run() {
            if(!page.isAttachedToWindow) return
            val active=NativeNavigation.running
            empty.visibility=if(active) View.GONE else View.VISIBLE
            image.visibility=if(active) View.VISIBLE else View.GONE
            zoom.visibility=if(active) View.VISIBLE else View.GONE
            tripHud.visibility=if(active) View.VISIBLE else View.GONE
            if(active) {
                image.setImageBitmap(NativeNavigation.phoneBitmap(RidePreferences.prefs(activity).getString("phone_panel","music") ?: "map"))
                val trip = NativeNavigation.cockpitTrip()
                if (trip != null) {
                    val eta = trip.arrivalMillis?.let { SimpleDateFormat("HH:mm", Locale.US).format(
                        Date(it)
                    ) } ?: "--:--"
                    val km = trip.remainingMeters / 1000.0
                    val min = (trip.remainingMillis ?: 0L) / 60000L
                    tripEtaView.text = String.format(Locale.US, "ETA %s · %.1f km (%d min)", eta, km, min)
                } else {
                    tripEtaView.text = "GPS active"
                }
                val fuelText = FuelEstimateStore.mapLabel(activity)
                tripFuelView.text = if (!fuelText.isNullOrBlank()) "⛽ $fuelText" else "⛽ Est. fuel range ~180 km"
            }
            directions.text=NativeNavigation.phoneGuidance()
            gps.text=NativeNavigation.gpsSummary()
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
