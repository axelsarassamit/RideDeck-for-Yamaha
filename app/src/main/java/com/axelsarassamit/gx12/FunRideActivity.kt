package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.location.Location
import android.content.Intent
import android.widget.*
import app.pillion.core.FunPoint
import app.pillion.core.FunRidePlan
import java.util.concurrent.Executors

/** Parked planning. No running navigation is changed until Start in the map preview. */
class FunRideActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor();private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    private var searchGeneration=0
    private val controls=mutableListOf<Button>()
    private var startPlace:NavigationPlace?=null;private var finishPlace:NavigationPlace?=null
    private val stops=mutableListOf<NavigationPlace>()
    private lateinit var startButton:Button;private lateinit var finishButton:Button;private lateinit var stopButton:Button
    private lateinit var status:TextView;private lateinit var build:Button;private lateinit var duration:Spinner;private lateinit var style:Spinner;private lateinit var direction:Spinner
    private fun button(label:String,action:()->Unit)=Button(this).apply { text=label;RideStyle.action(this,true);setOnClickListener{action()};controls.add(this) }
    override fun onCreate(saved:Bundle?) {
        RideAppearance.prepare(this);super.onCreate(saved);NativeNavigation.start(this)
        startPlace=ItineraryRoutes.decode(saved?.getString("start")).firstOrNull();finishPlace=ItineraryRoutes.decode(saved?.getString("finish")).firstOrNull();stops.addAll(ItineraryRoutes.decode(saved?.getString("stops")))
        val page=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(20,24,20,24);setBackgroundColor(RideStyle.BACKGROUND)}
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { v,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val pad=(16*resources.displayMetrics.density).toInt();v.setPadding(bars.left+pad,bars.top+pad,bars.right+pad,bars.bottom+pad);insets
        }
        fun text(value:String,size:Float=18f)=TextView(this).apply{text=value;textSize=size;setTextColor(RideStyle.TEXT)}
        page.addView(text("Go for a fun ride",28f));page.addView(text("Plan while parked. Routes follow mapped roads; scenery and motorcycle access are not guaranteed. Review the whole map before riding."))
        startButton=button(startPlace?.label ?: "Start: current location") { selectPlace("Starting point",true){startPlace=it;startButton.text="Start: ${it.label}"} };page.addView(startButton)
        page.addView(text("How much riding time? Stops are extra."));duration=Spinner(this).apply{adapter=ArrayAdapter(this@FunRideActivity,android.R.layout.simple_spinner_dropdown_item,listOf("30 minutes","1 hour","90 minutes","2 hours","3 hours","4 hours","6 hours"));setSelection(saved?.getInt("duration",1) ?: 1);minimumHeight=(76*resources.displayMetrics.density).toInt()};page.addView(duration)
        finishButton=button(finishPlace?.let{"Finish: ${it.label}"} ?: "Finish: return to start") { AlertDialog.Builder(this).setTitle("Finish ride").setItems(arrayOf("Return to start","Saved Home","Choose a place")){_,n->if(n==0){finishPlace=null;finishButton.text="Finish: return to start"}else if(n==1){val home=NavigationApi.coordinate(RidePreferences.prefs(this).getString("bike_home","").orEmpty());if(home==null)Toast.makeText(this,"Save Home coordinates first",Toast.LENGTH_LONG).show()else{finishPlace=home.copy(label="Home");finishButton.text="Finish: Home"}}else selectPlace("Finish",false){finishPlace=it;finishButton.text="Finish: ${it.label}"}}.show() };page.addView(finishButton)
        page.addView(text("Ride style"));style=Spinner(this).apply{adapter=ArrayAdapter(this@FunRideActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Relaxed exploration","Winding roads · TomTom only"));setSelection(saved?.getInt("style",0) ?: 0);minimumHeight=(76*resources.displayMetrics.density).toInt()};page.addView(style)
        page.addView(text("Explore towards"));direction=Spinner(this).apply{adapter=ArrayAdapter(this@FunRideActivity,android.R.layout.simple_spinner_dropdown_item,listOf("North","East","South","West"));setSelection(saved?.getInt("direction",0) ?: 0);minimumHeight=(76*resources.displayMetrics.density).toInt()};page.addView(direction)
        stopButton=button("Stops: ${stops.size} of 2") { if(stops.size>=2) AlertDialog.Builder(this).setTitle("Stops").setItems((stops.map{"Remove ${it.label}"}).toTypedArray()){_,i->stops.removeAt(i);stopButton.text="Stops: ${stops.size} of 2"}.show() else selectPlace("Add a stop · search coffee, fuel or a place",false){stops.add(it);stopButton.text="Stops: ${stops.size} of 2"} };page.addView(stopButton)
        page.addView(button("Roads to avoid") { RoutePreferencesUi.show(this) })
        status=text("Up to three route choices. Duration is a target, not a promise. TomTom uses available traffic; Valhalla has no live traffic.");page.addView(status)
        build=button("Find rides") { plan() };page.addView(build);page.addView(button("Back") { finish() });setContentView(ScrollView(this).apply{addView(page)})
    }
    private fun selectPlace(title:String,current:Boolean,chosen:(NavigationPlace)->Unit) {
        val searchToken=++searchGeneration
        val input=EditText(this).apply{hint="Place, address or latitude, longitude";textSize=20f}
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search",null)
        if(current)dialog.setNeutralButton("Current location"){_,_->startPlace=null;startButton.text="Start: current location"}
        val d=dialog.create();d.setOnDismissListener { if(searchToken==searchGeneration)searchGeneration++ };d.setOnShowListener{d.getButton(-1).setOnClickListener{
            val query=input.text.toString().trim();if(query.isBlank()){input.error="Enter a place";return@setOnClickListener}
            d.getButton(-1).isEnabled=false
            worker.execute {val result=runCatching{NavigationApi.search(this,query,NativeNavigation.location)};main.post{
                if(isFinishing || isDestroyed || !d.isShowing || searchToken!=searchGeneration)return@post;d.getButton(-1).isEnabled=true
                result.onSuccess{rows->if(rows.isEmpty())input.error="No places found. Try coordinates or another name." else {d.dismiss();AlertDialog.Builder(this).setTitle("Choose the exact place").setItems(rows.map{it.label}.toTypedArray()){_,i->chosen(rows[i])}.show()}}.onFailure{input.error="Search unavailable. Try again."}
            }}
        }};d.show()
    }
    private fun busy(value:Boolean){controls.filter{it.text.toString()!="Back"}.forEach{it.isEnabled=!value};duration.isEnabled=!value;style.isEnabled=!value;direction.isEnabled=!value}
    private fun plan() {
        val fix=startPlace?.let{Location("chosen").apply{latitude=it.latitude;longitude=it.longitude}} ?: NativeNavigation.location?.takeIf{it.hasAccuracy() && NavigationFixQuality.accept(NavigationFix(it.latitude,it.longitude,it.accuracy.toDouble(),it.elapsedRealtimeNanos,it.provider==android.location.LocationManager.GPS_PROVIDER),null,SystemClock.elapsedRealtimeNanos())}?.let(::Location)
        if(fix==null){status.text="Waiting for a fresh location. Move outdoors or choose your starting point.";return}
        if(fix.latitude !in -85.0..85.0){status.text="This planner supports locations between 85 degrees south and north.";return}
        val winding=style.selectedItemPosition==1
        if(winding && (!TomTomApi.configured(this)||!RidePreferences.prefs(this).getBoolean("tomtom_routing",true))){status.text="Winding routes need TomTom routing enabled. Choose relaxed exploration or set up TomTom.";return}
        val minutes=listOf(30,60,90,120,180,240,360)[duration.selectedItemPosition];val heading=direction.selectedItemPosition*90.0
        val end=finishPlace ?: NavigationPlace("Return to start",fix.latitude,fix.longitude)
        val chosenStops=stops.toList();val request=++generation;busy(true);status.text="Finding roads in three directions... You can go Back to cancel."
        worker.execute {
            val candidates=mutableListOf<NavigationRoute>();var lastRequest=0L;var scale=1.0;var failures=0
            for(bearing in listOf(heading,heading+35,heading-35)) {
                if(Thread.currentThread().isInterrupted || request!=generation)break
                val elapsed=SystemClock.elapsedRealtime()-lastRequest
                if(elapsed<1200)try { Thread.sleep(1200-elapsed) } catch(_:InterruptedException) { Thread.currentThread().interrupt();return@execute }
                val seeds=runCatching { FunRidePlan.seeds(FunPoint(fix.latitude,fix.longitude),minutes,bearing,scale).mapIndexed{i,p->NavigationPlace("Explore via ${i+1}",p.latitude,p.longitude)} }.getOrNull() ?: continue
                val via=if(chosenStops.isEmpty())seeds else chosenStops
                lastRequest=SystemClock.elapsedRealtime()
                val routed=runCatching{NavigationApi.itinerary(this,fix,via+end,if(winding)"winding" else "relaxed")}.recoverCatching { original ->
                    if(winding || !TomTomApi.configured(this) || !RidePreferences.prefs(this).getBoolean("tomtom_routing",true))throw original
                    // Relaxed rides can use the same free road provider as Standard. Winding never silently loses its style.
                    main.post{if(request==generation && !isDestroyed)status.text="TomTom could not plan this ride. Trying free road routing; no live traffic."}
                    NavigationApi.freeItinerary(this,fix,via+end)
                }
                routed.onSuccess { r ->
                    val actual=r.millis/60000.0
                    if(r.meters>=1000 && actual>0 && actual<=720)candidates.add(r)
                    if(chosenStops.isEmpty() && actual>0)scale=(scale*(minutes/actual).coerceIn(.5,2.0)).coerceIn(.25,4.0)
                }.onFailure { failures++ }

                // Fixed requested stops produce the same itinerary; a single request is sufficient.
                if(chosenStops.isNotEmpty())break
            }
            main.post {
                if(isDestroyed || request!=generation)return@post;busy(false)
                val matching=candidates.filter{it.millis/60000.0 in (minutes*.4)..(minutes*2.0)}
                val distinct=RouteChoices.ranked(matching.ifEmpty{candidates}).sortedBy{FunRidePlan.score(it.millis/60000.0,minutes,it.turns.count{t->kotlin.math.abs(t.sign)==8})}
                if(distinct.isEmpty()){status.text=if(failures>0) "Road routing could not build a ride. Check your connection, try another direction or relax road preferences. Winding rides require working TomTom routing." else "No usable ride returned. Try a shorter time or choose your own stops.";return@post}
                FunRidePreview.put(distinct,fix,minutes);startActivity(Intent(this,RoutePreviewActivity::class.java).putExtra("funRide",true).putExtra("lat",end.latitude).putExtra("lon",end.longitude).putExtra("label",end.label))
            }
        }
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putString("start",ItineraryRoutes.encode(listOfNotNull(startPlace)));out.putString("finish",ItineraryRoutes.encode(listOfNotNull(finishPlace)));out.putString("stops",ItineraryRoutes.encode(stops));out.putInt("duration",duration.selectedItemPosition);out.putInt("style",style.selectedItemPosition);out.putInt("direction",direction.selectedItemPosition)}
    override fun onDestroy(){searchGeneration++;generation++;worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy()}
}
/** Short-lived handoff into the existing full-size map. Process loss asks for a new plan. */
object FunRidePreview {
    data class Plan(val routes:List<NavigationRoute>,val origin:Location,val minutes:Int)
    private var plan:Plan?=null
    @Synchronized fun put(routes:List<NavigationRoute>,origin:Location,minutes:Int){plan=Plan(routes,Location(origin),minutes)}
    @Synchronized fun take()=plan.also{plan=null}
}
