package com.axelsarassamit.gx12

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import app.pillion.core.RouteTimeEstimator
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import java.util.concurrent.Executors

/** Planning is separate from the running route. Only Start commits the selected geometry. */
class RoutePreviewActivity : Activity() {
    companion object {
        fun open(activity: Activity, target: NavigationPlace) {
            activity.startActivity(Intent(activity, RoutePreviewActivity::class.java)
                .putExtra("label",target.label).putExtra("lat",target.latitude).putExtra("lon",target.longitude))
        }
    }
    private data class Plan(val routes: List<NavigationRoute>, val origin: Location?, val selected: Int, val style: String?,val target: NavigationPlace)
    private val worker=Executors.newSingleThreadExecutor()
    private val parkingWorker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private lateinit var target: NavigationPlace
    private lateinit var goal: NavigationPlace
    private lateinit var destinationLabel: TextView
    private lateinit var parking: Button
    private var parkingOptions=emptyList<app.pillion.core.ParkingSuggestion>()
    private var parkingBusy=false
    private var parkingFailed=false
    private lateinit var mapView: MapView
    private var map: MapLibreMap?=null
    private lateinit var status: TextView
    private lateinit var choices: LinearLayout
    private lateinit var choiceScroll: ScrollView
    private lateinit var mapStatus: TextView
    private lateinit var start: Button
    private lateinit var retry: Button
    private var routes=emptyList<NavigationRoute>()
    private var origin: Location?=null
    private var selected=0
    private var styleJson: String?=null
    private var styleReady=false
    private var busy=false
    private var routeRequest=0
    private val lineChoices=HashMap<Long,Int>()
    private val blue=0xff087eff.toInt()
    private fun dp(value: Int)=(resources.displayMetrics.density*value).toInt()
    private fun button(label: String, primary: Boolean=false)=Button(this).apply {
        text=label; isAllCaps=false; textSize=22f; minHeight=dp(80)
        setTextColor(Color.WHITE)
        background=GradientDrawable().apply { setColor(if(primary) blue else 0xff242f40.toInt()); cornerRadius=dp(14).toFloat() }
        setPadding(dp(12),dp(8),dp(12),dp(8))
    }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val lat=intent.getDoubleExtra("lat",Double.NaN); val lon=intent.getDoubleExtra("lon",Double.NaN)
        if(lat !in -90.0..90.0 || lon !in -180.0..180.0) { finish(); return }
        target=NavigationPlace(intent.getStringExtra("label") ?: "Destination",lat,lon)
        goal=target
        (lastNonConfigurationInstance as? Plan)?.let { routes=it.routes; origin=it.origin; selected=it.selected; styleJson=it.style;target=it.target }
        MapLibre.getInstance(this)
        NativeNavigation.start(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor=0xff101722.toInt(); window.navigationBarColor=0xff101722.toInt()
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xff101722.toInt()); setPadding(dp(12),dp(8),dp(12),dp(8)) }
        page.setOnApplyWindowInsetsListener { v,insets ->
            val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            v.setPadding(bars.left+dp(12),bars.top+dp(8),bars.right+dp(12),bars.bottom+dp(8)); insets
        }
        val header=LinearLayout(this)
        val titles=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        val heading=TextView(this).apply { text="Choose your route"; textSize=24f; setTextColor(Color.WHITE) }
        titles.addView(heading)
        destinationLabel=TextView(this).apply { text=target.label; textSize=18f; maxLines=2; setTextColor(0xffb9c5d6.toInt()) }
        titles.addView(destinationLabel)
        parking=button("Parking\nSearching").apply {
            minHeight=dp(72);textSize=16f;setOnClickListener { showParking() }
        }
        header.addView(parking,LinearLayout.LayoutParams(dp(100),dp(72)).apply { leftMargin=dp(8) })
        page.addView(header,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val wide=resources.configuration.screenWidthDp>=600
        val body=LinearLayout(this).apply { orientation=if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val mapArea=FrameLayout(this)
        body.addView(mapArea,if(wide) LinearLayout.LayoutParams(0,-1,1f) else LinearLayout.LayoutParams(-1,0,1f))
        mapView=MapView(this); mapView.onCreate(saved); mapArea.addView(mapView,FrameLayout.LayoutParams(-1,-1))
        mapStatus=TextView(this).apply { text="Loading map..."; textSize=18f; setTextColor(Color.WHITE); setBackgroundColor(0xee101722.toInt()); setPadding(dp(12),dp(12),dp(12),dp(12)); gravity=Gravity.CENTER }
        mapArea.addView(mapStatus,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        val zoom=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        listOf("+","−").forEachIndexed { i,text -> zoom.addView(button(text).apply {
            contentDescription=if(i==0) "Zoom in" else "Zoom out"
            setOnClickListener { map?.animateCamera(if(i==0) CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut()) }
        },LinearLayout.LayoutParams(dp(72),dp(72)).apply { bottomMargin=dp(8) }) }
        mapArea.addView(zoom,FrameLayout.LayoutParams(-2,-2,Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin=dp(8) })
        mapArea.addView(button("Overview").apply { textSize=18f; minHeight=dp(56); setOnClickListener { overview() } },FrameLayout.LayoutParams(dp(132),dp(56),Gravity.END or Gravity.TOP).apply { topMargin=dp(8); rightMargin=dp(8) })
        mapArea.addView(TextView(this).apply {
            text=android.text.Html.fromHtml("© <a href='https://www.maptiler.com/copyright/'>MapTiler</a> © <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>",0)
            textSize=12f; setTextColor(Color.WHITE); setBackgroundColor(0xdd101722.toInt()); setPadding(dp(8),dp(4),dp(8),dp(4))
            movementMethod=android.text.method.LinkMovementMethod.getInstance()
        },FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        val controls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(if(wide) dp(12) else 0,dp(8),0,0) }
        body.addView(controls,if(wide) LinearLayout.LayoutParams(dp(300),-1) else LinearLayout.LayoutParams(-1,-2))
        status=TextView(this).apply { textSize=18f; setTextColor(Color.WHITE); setPadding(0,0,0,dp(8)) }
        controls.addView(status)
        choices=LinearLayout(this).apply { orientation=if(wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        choiceScroll=ScrollView(this).apply { addView(choices) }
        if(wide) controls.addView(choiceScroll,LinearLayout.LayoutParams(-1,0,1f))
        else { choiceScroll.removeView(choices);controls.addView(choices,LinearLayout.LayoutParams(-1,dp(96))) }
        start=button("Start navigation",true).apply { isEnabled=false; setOnClickListener { startChosen() } }
        retry=button("Retry routes").apply { setOnClickListener { loadRoutes() } }
        controls.addView(retry,LinearLayout.LayoutParams(-1,dp(64)).apply { topMargin=dp(8) })
        val footer=LinearLayout(this)
        footer.addView(start,LinearLayout.LayoutParams(0,dp(80),1f).apply { rightMargin=dp(8) })
        footer.addView(button("Cancel").apply { setOnClickListener { finish() } },LinearLayout.LayoutParams(dp(104),dp(80)))
        controls.addView(footer,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        setContentView(page)
        mapView.getMapAsync { ready ->
            map=ready; ready.uiSettings.isLogoEnabled=false; ready.uiSettings.isAttributionEnabled=false
            ready.setOnPolylineClickListener { line -> lineChoices[line.id]?.let { select(it) } }
            loadStyle()
        }
        if(routes.isEmpty()) { status.text="Getting your GPS position..."; main.post(awaitGps) } else showChoices()
        findParking()
    }
    private fun findParking() {
        if(parkingBusy) return
        parkingBusy=true;parkingFailed=false;parking.text="Parking\nSearching"
        parkingWorker.execute {
            val result=runCatching { ParkingApi.near(goal) }
            main.post {
                if(isFinishing || isDestroyed) return@post
                parkingBusy=false
                result.onSuccess { parkingOptions=it
                    parking.text=if(it.isEmpty()) "Parking\nNone mapped" else "Parking\n${it.size} found"
                    parking.contentDescription=when { it.isEmpty() -> "No parking mapped nearby";it.any { option -> option.motorcycleConfirmed } -> "Motorcycle parking suggestions";else -> "Nearby parking; motorcycle access unconfirmed" }
                    BikeDiagnostics.record(this,"Parking suggestions ready count=${it.size}")
                }.onFailure { parkingFailed=true;parking.text="Parking\nRetry";BikeDiagnostics.record(this,"Parking search failed exception=${it.javaClass.simpleName} ${it.message?.takeIf { message -> message.startsWith("Parking search") }.orEmpty()}") }
            }
        }
    }
    private fun showParking() {
        if(parkingBusy) { Toast.makeText(this,"Looking for motorcycle parking near your destination",Toast.LENGTH_SHORT).show();return }
        if(parkingFailed) { findParking();return }
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12)) }
        page.addView(TextView(this).apply {
            text=if(parkingOptions.isEmpty()) "No parking is recorded within 1.5 km. You can still route to your destination." else "Motorcycle parking comes first when mapped. General parking is marked when motorcycle access is unknown. Distances are approximate, straight to your destination. Check signs and spaces on arrival."
            textSize=18f
        })
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Motorcycle parking").setView(ScrollView(this).apply { addView(page) }).setNegativeButton("Close",null).create()
        parkingOptions.forEachIndexed { index,option ->
            val t=option.record.tags
            val name=t["name:en"] ?: t["name"] ?: when { option.dedicated -> "Motorcycle parking";option.motorcycleConfirmed -> "Parking with motorcycle spaces";else -> "General parking" }
            val fee=when(option.fee) { "no" -> "No fee recorded"; "yes" -> "Paid parking"; else -> "Fee unknown" }
            val access=when(option.access) { "customers" -> " · Customers only"; "yes","public","permissive","designated" -> ""; else -> " · Access unconfirmed" }
            val hours=t["opening_hours"]?.let { "\nHours: $it" }.orEmpty()
            val motorcycle=if(option.motorcycleConfirmed) "Motorcycles recorded" else "Motorcycle access unconfirmed"
            page.addView(button((if(index==0 && option.motorcycleConfirmed) "Suggested: " else "")+name+"\n$motorcycle\n~${option.meters.toInt()} m from destination · $fee$access$hours").apply {
                textSize=18f;setOnClickListener {
                    dialog.dismiss();chooseDestination(NavigationPlace("$name · parking for ${goal.label}",option.record.latitude,option.record.longitude))
                }
            },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        }
        if(target!=goal) page.addView(button("Route directly to destination").apply { setOnClickListener { dialog.dismiss();chooseDestination(goal) } },LinearLayout.LayoutParams(-1,dp(80)).apply { topMargin=dp(10) })
        dialog.show()
    }
    private fun chooseDestination(place: NavigationPlace) {
        routeRequest++
        target=place;destinationLabel.text=place.label;routes=emptyList();choices.removeAllViews();start.isEnabled=false
        map?.clear();lineChoices.clear();loadRoutes()
    }
    private val awaitGps=object: Runnable {
        override fun run() {
            if(isFinishing || isDestroyed) return
            if(freshFix()!=null) loadRoutes() else {
                status.text="Waiting for GPS. Move outdoors, then Retry."
                main.postDelayed(this,1000)
            }
        }
    }
    private fun freshFix()=NativeNavigation.location?.takeIf { SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos < 30_000_000_000L }
    private fun loadStyle() {
        val cached=styleJson
        if(cached!=null) { applyStyle(cached); return }
        worker.execute {
            val result=runCatching { NavigationApi.englishMapStyle(applicationContext,NativeNavigation.isNightMap()) }
            main.post {
                if(isFinishing || isDestroyed) return@post
                result.onSuccess { styleJson=it; applyStyle(it) }.onFailure { mapStatus.text="Map unavailable. Check internet and MapTiler settings, then Retry." }
            }
        }
    }
    private fun applyStyle(json: String) {
        map?.setStyle(Style.Builder().fromJson(json)) {
            if(isFinishing || isDestroyed) return@setStyle
            styleReady=true; mapStatus.visibility=View.GONE; drawRoutes(); if(routes.isNotEmpty()) overview()
        }
    }
    private fun loadRoutes() {
        retry.visibility=View.VISIBLE
        val fix=freshFix()
        if(fix==null) { status.text="Waiting for GPS. Move outdoors, then Retry."; return }
        val generation=++routeRequest
        val destination=target
        val plannedOrigin=Location(fix)
        main.removeCallbacks(awaitGps)
        busy=true; retry.isEnabled=false; start.isEnabled=false; status.text="Comparing motorcycle routes..."
        if(map!=null && styleJson==null) loadStyle()
        worker.execute {
            val result=runCatching { NavigationApi.routes(plannedOrigin,destination) }
            main.post {
                if(isFinishing || isDestroyed || generation!=routeRequest) return@post
                busy=false; retry.isEnabled=true
                result.onSuccess { origin=plannedOrigin;routes=it; selected=0; showChoices(); drawRoutes(); overview()
                    BikeDiagnostics.record(this,"Route preview ready options=${routes.size}")
                }.onFailure { status.text="Routes unavailable. Check internet, then Retry."; choices.removeAllViews(); routes=emptyList(); map?.clear(); lineChoices.clear()
                    BikeDiagnostics.record(this,"Route preview failed exception=${it.javaClass.simpleName}")
                }
            }
        }
    }
    private fun showChoices() {
        choices.removeAllViews()
        val wide=resources.configuration.screenWidthDp>=600
        status.text=if(routes.size==1) "One route available" else "${routes.size} routes · Route ${selected+1} selected"
        retry.visibility=View.GONE
        routes.forEachIndexed { index,r ->
            choices.addView(button((if(index==selected) "✓ " else "")+"Route ${index+1}"+"\n"+
                RouteTimeEstimator.durationText(r.millis)+(if(wide) "  ·  " else "\n")+String.format(Locale.US,"%.1f km",r.meters/1000),index==selected).apply {
                textSize=if(wide) 20f else 18f;gravity=if(wide) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER;setPadding(dp(4),dp(4),dp(4),dp(4));setOnClickListener { select(index) }
            },if(wide) LinearLayout.LayoutParams(-1,dp(72)).apply { bottomMargin=dp(8) }
                else LinearLayout.LayoutParams(0,dp(96),1f).apply { rightMargin=if(index<routes.lastIndex) dp(8) else 0 })
        }
        start.isEnabled=routes.isNotEmpty() && !busy
    }
    private fun select(index: Int) { selected=index; showChoices(); drawRoutes() }
    private fun drawRoutes() {
        val m=map ?: return
        if(!styleReady || routes.isEmpty()) return
        m.clear(); lineChoices.clear()
        (routes.indices.filter { it!=selected }+selected).forEach { index ->
            val line=m.addPolyline(PolylineOptions().addAll(routes[index].points).color(if(index==selected) blue else 0xff8393ad.toInt()).width(if(index==selected) 7f else 5f))
            lineChoices[line.id]=index
        }
        origin?.let { m.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title("Start")) }
        m.addMarker(MarkerOptions().position(LatLng(target.latitude,target.longitude)).title("Destination"))
        if(target!=goal) m.addMarker(MarkerOptions().position(LatLng(goal.latitude,goal.longitude)).title("Final destination after parking"))
    }
    private fun overview() {
        if(!styleReady || routes.isEmpty()) return
        val bounds=LatLngBounds.Builder().includes(routes.flatMap { it.points }+LatLng(goal.latitude,goal.longitude)).build()
        mapView.post { if(!isDestroyed) map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,dp(48),dp(70),dp(48),dp(48))) }
    }
    private fun startChosen() {
        val r=routes.getOrNull(selected) ?: return
        if(busy || r.destination!=target) return
        val fix=freshFix(); val planned=origin
        if(fix==null) { status.text="GPS signal lost. Wait for a fix, then Start navigation."; return }
        if(planned==null || fix.distanceTo(planned)>100) { status.text="Your position changed. Updating routes..."; loadRoutes(); return }
        NativeNavigation.startSelected(this,r)
        startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
    override fun onRetainNonConfigurationInstance(): Any=Plan(routes,origin,selected,styleJson,target)
    override fun onStart() { super.onStart(); if(::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if(::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if(::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if(::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); if(::mapView.isInitialized) mapView.onSaveInstanceState(out) }
    override fun onLowMemory() { super.onLowMemory(); if(::mapView.isInitialized) mapView.onLowMemory() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow();parkingWorker.shutdownNow(); if(::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }
}
