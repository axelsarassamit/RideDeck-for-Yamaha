package com.axelsarassamit.gx12

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

object NavigationUi {
    private val worker=Executors.newFixedThreadPool(2)
    private val main=Handler(Looper.getMainLooper())
    /** Always visible on the riding cockpit; these are actions, not setup menus. */
    @JvmStatic fun quickActions(activity:Activity):LinearLayout {
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        return LinearLayout(activity).apply {
            tag="ride_quick_actions"
            val actions=listOf<Pair<String,()->Unit>>(
                "Search" to { search(activity,null) },
                "Fun ride" to { activity.startActivity(Intent(activity,FunRideActivity::class.java)) },
                "Ride tools" to { RideToolsActivity.open(activity) })
            val visibleActions=if(activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT && !activity.isInMultiWindowMode) actions.drop(1) else actions
            for((label,action) in visibleActions) addView(Button(activity).apply {
                text=label;contentDescription="Ride action: $label";isAllCaps=false;textSize=18f;maxLines=2;minHeight=dp(56)
                gravity=Gravity.CENTER;setPadding(dp(3),dp(2),dp(3),dp(2));setTextColor(RideStyle.TEXT)
                background=RideStyle.row(activity);setOnClickListener{action()}
            },LinearLayout.LayoutParams(0,dp(56),1f).apply{leftMargin=dp(3);rightMargin=dp(3)})
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
    @JvmStatic fun mapMenu(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Map")
            .setItems(arrayOf("Search destination", "Stop route", "Voice guidance", "Road preferences", "Estimated fuel range", "Go for a fun ride")) { _, choice ->
                when (choice) {
                    0 -> search(activity, null)
                    1 -> NativeNavigation.stopRoute()
                    2 -> voiceGuidance(activity)
                    3 -> RoutePreferencesUi.show(activity)
                    4 -> FuelRangeActivity.open(activity)
                    5 -> activity.startActivity(Intent(activity,FunRideActivity::class.java))
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
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(RideStyle.BACKGROUND) }
        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Map; tap for navigation menu"; setOnClickListener { mapMenu(activity) } }
        val state=TextView(activity).apply {
            setTextColor(RideStyle.TEXT); gravity=Gravity.CENTER_VERTICAL; textSize=11f; setPadding(8,4,8,4)
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
        val directions=TextView(activity).apply {
            textSize=22f;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(RideStyle.TEXT)
            setPadding(12,6,12,6);setBackgroundColor(RideStyle.PANEL);contentDescription="Next navigation instruction"
        }
        page.addView(directions,LinearLayout.LayoutParams(-1,-2))
        val body=LinearLayout(activity)
        val mapArea=FrameLayout(activity)
        mapArea.addOnLayoutChangeListener { _,left,top,right,bottom,_,_,_,_ -> NativeNavigation.phoneViewport(right-left,bottom-top) }
        mapArea.addView(image,FrameLayout.LayoutParams(-1,-1))
        val empty=TextView(activity).apply {
            text="Your route appears here\nChoose a destination to begin";gravity=Gravity.CENTER;textSize=18f
            setTextColor(RideStyle.MUTED);setPadding(20,16,20,16)
            setOnClickListener { search(activity,null) }
        }
        mapArea.addView(empty,FrameLayout.LayoutParams(-1,-1))
        body.addView(mapArea,LinearLayout.LayoutParams(0,-1,1f))
        val density=activity.resources.displayMetrics.density
        fun dp(value:Int)=(value*density).toInt()
        val zoom=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setBackgroundColor(RideStyle.PANEL) }
        listOf("+","−").forEachIndexed { index,label ->
            zoom.addView(Button(activity).apply {
                text=label;textSize=30f;isAllCaps=false;minWidth=0;minimumWidth=0
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
            if(active) image.setImageBitmap(NativeNavigation.phoneBitmap(RidePreferences.prefs(activity).getString("phone_panel","music") ?: "map"))
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
