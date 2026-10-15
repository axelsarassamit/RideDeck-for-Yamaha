package com.axelsarassamit.gx12

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.*
import app.pillion.core.RouteTimeEstimator
import app.pillion.core.DashRoute
import app.pillion.core.DashTurn
import app.pillion.core.RoadSpeedLimits
import app.pillion.core.SpeedLimitWindow
import app.pillion.core.SolarMapTheme
import android.content.res.Configuration
import android.os.*
import android.speech.tts.TextToSpeech
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.*

/** Own map pixels and GPS. No screenshots, virtual displays or debugging privileges. */
object NativeNavigation {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val speedWorker = Executors.newSingleThreadExecutor()
    private val styleWorker = Executors.newSingleThreadExecutor()
    private val incidentWorker = Executors.newSingleThreadExecutor()
    private var speedWindow: SpeedLimitWindow? = null
    private var speedRequestBusy = false
    private var lastSpeedRequest = -30000L
    @Volatile private var speedLimit: Int? = null
    private var context: Context? = null
    @Volatile var running = false
    @Volatile var location: Location? = null
    @Volatile var route: NavigationRoute? = null
    @Volatile var dashRoute: DashRoute? = null
        private set
    @Volatile var status = "Start navigation to get a GPS position"
    @Volatile var mapBitmap: Bitmap? = null
    @Volatile private var mapJpeg: ByteArray? = null
    @Volatile var acquisitionStatus = "Waiting for a GPS fix. Move outdoors if needed"
    @Volatile private var guidance = "Waiting for GPS"
    @Volatile private var maneuver = 0
    private val mapZoom = app.pillion.core.MapZoom()
    private var height = 234
    private var snapshotter: MapSnapshotter? = null
    private var phoneSnapshotter: MapSnapshotter? = null
    private var phoneHeight = 234
    private var phoneVisible = false
    private var phoneInFlight = false
    private var phoneFrameAt = 0L
    private var phoneRenderStarted = 0L
    private var phoneMapBitmap: Bitmap? = null
    private var englishStyle: String? = null
    private var styleDark: Boolean? = null
    private val styleCache = HashMap<Boolean,String>()
    private var styleGeneration = 0
    @Volatile private var lastSolarNight: Boolean? = null
    private var styleLoading = false
    private var styleRetryAt = 0L
    private var inFlight = false
    private var frameAt = 0L
    @Volatile private var renderedZoomRevision = -1L
    private var renderStarted = 0L
    private var routeGeneration = 0
    private var dashRouteRevision = 0
    private var routeBusy = false
    private var pendingDestination: NavigationPlace? = null
    private var progressIndex = 0
    private var remaining = 0.0
    private var lastReroute = 0L
    private var offRouteSamples = 0
    private val announcements = NavigationAnnouncements()
    private val trafficAnnouncements = TrafficAnnouncements()
    private val incidentAnnouncements = IncidentAnnouncements()
    private var incidentBusy=false
    private var lastIncidentRequest=-300000L
    private var incidentCheckedAt:Long?=null
    private var incidentRevision=-1
    private var incidentRequestedRevision=-1
    private var incidentMatches=emptyList<RouteIncident>()
    private var travelledMeters=0.0
    private var lastClosureCheck=-60000L
    private val checkedClosures=HashMap<String,Long>()
    @Volatile private var incidentStatus="Start navigation to check accident and road-closure reports near the route."
    @JvmStatic fun trafficAlertSummary():String=when {
        route==null -> "Start navigation to check accident and road-closure reports near the route."
        context?.let{!RidePreferences.prefs(it).getBoolean("tomtom_incident_alerts",true)}==true -> "Accident and road-closure alerts are off in Map and routing accounts."
        incidentCheckedAt?.let{SystemClock.elapsedRealtime()-it>360000}==true -> "Incident reports are out of date. Waiting for a successful update."
        else -> incidentStatus
    }
    private var speaker: TextToSpeech? = null
    private var speechReady = false
    private var trafficRefreshBusy = false
    private var lastTrafficRefresh = 0L
    private var lastSummary = 0L

    @JvmStatic fun start(c: Context) {
        context = c.applicationContext
        runCatching { NavigationSecrets.importDebugSetup(c) }
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status = "Allow precise location in Setup to start navigation"; return
        }
        if (!running) {
            try { c.startForegroundService(Intent(c, NativeNavigationService::class.java)) }
            catch (_: Exception) { status = "Open RideDeck and start navigation again" }
        }
    }
    fun attach(c: Context) {
        context = c.applicationContext; running = true
        acquisitionStatus = "Waiting for a GPS fix. Move outdoors if needed"
        MapLibre.getInstance(c)
        speaker = TextToSpeech(c) { result ->
            speechReady = result == TextToSpeech.SUCCESS && speaker?.let(NavigationVoice::configure) == true
            BikeDiagnostics.record(c, "Navigation voice ready=$speechReady locale=${speaker?.voice?.locale?.toLanguageTag().orEmpty()}")
            if (!speechReady) BikeDiagnostics.record(c, "Navigation voice unavailable; install English speech data in Android speech settings")
        }
        speaker?.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        main.removeCallbacks(tick); main.post(tick)
        BikeDiagnostics.record(c, "Native navigation started renderer=MapLibre locationService=true")
    }
    fun detach() {
        running = false; routeGeneration++; routeBusy = false; pendingDestination = null
        main.removeCallbacks(tick); snapshotter?.cancel(); snapshotter = null; inFlight = false
        speaker?.shutdown(); speaker = null; speechReady = false
        phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false; phoneMapBitmap = null; englishStyle = null; styleLoading = false
        styleGeneration++; styleCache.clear(); styleDark = null; styleRetryAt = 0L; lastSolarNight = null
        location = null; route = null; mapJpeg = null; mapBitmap = null; dashRoute = null; speedWindow = null; speedLimit = null
        announcements.reset(); trafficAnnouncements.reset(); incidentMatches=emptyList();incidentCheckedAt=null;incidentAnnouncements.reset();incidentStatus="Navigation stopped. Incident checks paused."; status = "Navigation stopped"
    }
    fun resize(width: Int, h: Int) {
        require(width == 480 && h in listOf(234, 240))
        main.post { height = h; snapshotter?.cancel(); snapshotter = null; inFlight = false }
    }
    fun phoneViewport(width: Int, h: Int) {
        phoneVisible = width > 0 && h > 0
        if (!phoneVisible) { phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false; return }
        val next = (480.0 * h / width).roundToInt().coerceIn(120, 1600)
        if (next != phoneHeight) {
            phoneHeight = next; phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false
            phoneMapBitmap = null; phoneFrameAt = 0
        }
    }
    fun update(next: Location) {
        val now=SystemClock.elapsedRealtimeNanos()
        val previous=location
        if(!next.hasAccuracy() || !NavigationFixQuality.accept(quality(next),previous?.let(::quality),now)) {
            if(previous==null || now-previous.elapsedRealtimeNanos>10_000_000_000L) {
                speedLimit=null;acquisitionStatus="Waiting for a precise GPS fix";status=acquisitionStatus
            }
            return
        }
        location=Location(next)
        acquisitionStatus=if(next.provider==LocationManager.GPS_PROVIDER) "GPS connected" else "Network position; waiting for GPS"
        pendingDestination?.let { target ->
            pendingDestination = null
            context?.let { navigate(it, target) }
            return
        }
        if (route != null) status = "Navigating"
        else if (!routeBusy && !status.startsWith("Route unavailable:")) status = "GPS ready. Choose a destination"
        updateGuidance(next)
    }
    private fun quality(fix:Location)=NavigationFix(fix.latitude,fix.longitude,fix.accuracy.toDouble(),fix.elapsedRealtimeNanos,fix.provider==LocationManager.GPS_PROVIDER)
    private fun reliableSpeed(fix:Location)=NavigationFixQuality.speed(if(fix.hasSpeed()) fix.speed.toDouble() else null,
        if(fix.hasSpeedAccuracy()) fix.speedAccuracyMetersPerSecond.toDouble() else null)
    internal fun reliableHeading(fix:Location)=NavigationFixQuality.heading(reliableSpeed(fix),if(fix.hasBearing()) fix.bearing.toDouble() else null,
        if(fix.hasBearingAccuracy()) fix.bearingAccuracyDegrees.toDouble() else null)
    @JvmStatic fun gpsSummary():String {
        val fix=location ?: return acquisitionStatus
        val age=SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos
        if(age !in 0..5_000_000_000L) return "GPS signal weak; waiting for a fresh position"
        val source=if(fix.provider==LocationManager.GPS_PROVIDER) "GPS" else "Network"
        val speed=reliableSpeed(fix)?.let { " · ${kotlin.math.round(it*3.6).toInt()} km/h" }.orEmpty()
        return "$source ±${kotlin.math.ceil(fix.accuracy.toDouble()).toInt()} m$speed"
    }
    @JvmStatic fun phoneGuidance(): String = if (route != null) guidance else status
    @JvmStatic fun isNightMap(): Boolean {
        val fix=location
        val solar=fix?.let { SolarMapTheme.darkAt(System.currentTimeMillis(),it.latitude,it.longitude) }
        if(solar != null) lastSolarNight=solar
        return solar ?: lastSolarNight ?: (context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }
    @JvmStatic fun currentSpeedLimitKph(): Int? {
        val fix=location ?: return null
        if(!running || route == null || routeBusy || offRouteSamples > 0 || SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos >= 10_000_000_000L) return null
        return speedLimit
    }
    @JvmStatic fun cockpitTrip(): CockpitTrip? {
        val fix=location ?: return null
        val progress=dashRoute ?: return null
        if (!running || route == null || routeBusy || offRouteSamples > 0 || progress.revision != dashRouteRevision ||
            SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos >= 10_000_000_000L || !remaining.isFinite()) return null
        return CockpitTrip(progress.remainingMillis?.let { RouteTimeEstimator.arrivalMillis(System.currentTimeMillis(),it) },remaining.coerceAtLeast(0.0),progress.remainingMillis)
    }
    @JvmStatic fun phoneTripSummary(): String {
        if (route == null) return ""
        val fix=location
        if (fix == null || SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos >= 10_000_000_000L) return "Arrival time: waiting for GPS"
        if (routeBusy || offRouteSamples > 0) return "Recalculating arrival time"
        val duration=dashRoute?.remainingMillis ?: return "Calculating arrival time"
        val now=System.currentTimeMillis()
        val arrival=RouteTimeEstimator.arrivalMillis(now,duration)
        val today=java.util.Calendar.getInstance().apply { timeInMillis=now }
        val target=java.util.Calendar.getInstance().apply { timeInMillis=arrival }
        val day = if(today.get(java.util.Calendar.YEAR) != target.get(java.util.Calendar.YEAR) || today.get(java.util.Calendar.DAY_OF_YEAR) != target.get(java.util.Calendar.DAY_OF_YEAR))
            java.text.SimpleDateFormat("EEE",java.util.Locale.getDefault()).format(java.util.Date(arrival))+" " else ""
        val clock=context?.let { android.text.format.DateFormat.getTimeFormat(it).format(java.util.Date(arrival)) }
            ?: java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(arrival))
        return "Arrival $day$clock  |  ${RouteTimeEstimator.durationText(duration)}"
    }

    @JvmStatic fun stopRoute() {
        context?.let { RideBackgroundSession.clearDestination(it) }
        main.post { routeGeneration++; routeBusy = false; pendingDestination = null; route = null; dashRoute = null; speedWindow = null; speedLimit = null; guidance = "Choose a destination"; announcements.reset(); trafficAnnouncements.reset(); incidentMatches=emptyList();incidentCheckedAt=null;incidentAnnouncements.reset();progressIndex = 0; speaker?.stop() }
    }
    @JvmStatic fun routeText(text: String) {
        val c = context ?: error("Open RideDeck navigation first")
        val coordinate = NavigationApi.coordinate(text)
        check(coordinate != null) { "Choose this address from search suggestions on the phone first. Save coordinates for bike favorites." }
        main.post { navigate(c, coordinate) }
    }
    fun navigate(c: Context, target: NavigationPlace) {
        context = c.applicationContext
        RideBackgroundSession.saveDestination(c,target)
        val origin = location
        if (origin == null || SystemClock.elapsedRealtimeNanos() - origin.elapsedRealtimeNanos > 10_000_000_000L) {
            pendingDestination = target
            status = "Waiting for GPS to calculate the route"; return
        }
        val generation = ++routeGeneration
        val recalculating = route != null
        routeBusy = true; speedLimit = null; speedWindow = null; lastSpeedRequest = -30000L; dashRoute = dashRoute?.copy(remainingMillis=null); status = "Calculating route"
        worker.execute {
            val result = runCatching { NavigationApi.route(c, origin, target) }
            main.post {
                if (generation != routeGeneration) return@post
                routeBusy = false
                result.onSuccess {
                    applyRoute(c, it, recalculated = recalculating)
                    BikeDiagnostics.record(c, "Native route ready points=${it.points.size} instructions=${it.turns.size}")
                }.onFailure { error ->
                    val detail = when (error) {
                        is java.net.UnknownHostException -> "No internet connection."
                        is java.net.SocketTimeoutException -> "The routing service timed out. Try again."
                        else -> error.message?.takeIf { it.startsWith("Valhalla rejected the request") || it.startsWith("No motorcycle route") }
                            ?: "The motorcycle routing service could not calculate this route. Check your connection and try again."
                    }
                    status = "Route unavailable: $detail"
                    BikeDiagnostics.record(c, "Native route failed detail=$detail exception=${error.javaClass.simpleName}")
                }
            }
        }
    }
    fun startSelected(c: Context, selected: NavigationRoute) {
        context = c.applicationContext
        routeGeneration++; routeBusy = false; pendingDestination = null
        RideBackgroundSession.saveDestination(c, selected.destination)
        applyRoute(c, selected)
    }
    private fun applyRoute(c: Context, selected: NavigationRoute, announceStart: Boolean = true, recalculated: Boolean = false) {
        val previousIncidents=incidentMatches.map{it.incident};val previousCheck=incidentCheckedAt
        route = selected; dashRouteRevision++; dashRoute = null; progressIndex = 0; announcements.reset(announceStart,recalculated); offRouteSamples = 0
        if(announceStart && !recalculated) trafficAnnouncements.reset()
        if(announceStart && !recalculated) incidentAnnouncements.reset()
        incidentMatches=emptyList();incidentCheckedAt=null;travelledMeters=0.0
        if((!announceStart || recalculated) && previousIncidents.isNotEmpty() && previousCheck!=null && SystemClock.elapsedRealtime()-previousCheck<=360000 && !incidentBusy){
            val revision=dashRouteRevision;incidentBusy=true
            incidentWorker.execute {
                val matches=runCatching{RouteIncidentMatcher.match(selected.points.map{IncidentPoint(it.latitude,it.longitude)},selected.cumulative,0.0,previousIncidents)}
                main.post {incidentBusy=false;if(running && route===selected && dashRouteRevision==revision)matches.onSuccess{incidentMatches=it;incidentRevision=revision;incidentCheckedAt=previousCheck;invalidateFrames()}}
            }
        }
        trafficAnnouncements.report(if(selected.provider=="TomTom") selected.trafficDelayMillis.coerceIn(0,selected.millis) else 0,SystemClock.elapsedRealtime())
        speedLimit = null; speedWindow = selected.speedLimits?.let { SpeedLimitWindow(0,it) }; lastSpeedRequest = -30000L
        lastTrafficRefresh=SystemClock.elapsedRealtime()
        mapZoom.navigationStarted()
        status = "Navigating"; location?.let(::updateGuidance)
        invalidateFrames()
    }
    private fun invalidateFrames() {
        val previous = snapshotter; val previousPhone = phoneSnapshotter
        snapshotter = null; inFlight = false; frameAt = 0
        phoneSnapshotter = null; phoneInFlight = false; phoneFrameAt = 0
        // Cancel may deliver a callback synchronously. Detach first so it cannot publish an old frame.
        previous?.cancel(); previousPhone?.cancel()
    }
    @JvmStatic fun zoom(inside: Boolean) { main.post {
        val level = mapZoom.change(inside)
        invalidateFrames()
        context?.let { BikeDiagnostics.record(it, "Map zoom level=$level") }
        // Camera interaction can redraw the last position while GPS is stale.
        // Guidance and the bike's fresh-GPS flag continue to report the stale fix.
        if(running) location?.let { fix -> context?.let { c ->
            render(c,fix)
            if(phoneVisible) render(c,fix,true)
        } }
        main.removeCallbacks(tick); main.post(tick)
    } }
    @JvmStatic fun zoomState()=mapZoom.state()

    private fun updateGuidance(fix: Location) {
        val r = route ?: run { guidance = "Choose a destination"; maneuver = 0; return }
        // Project onto nearby segments, then measure along the route rather than direct distance.
        var bestDistance = Double.MAX_VALUE
        var bestIndex = progressIndex
        var bestFraction = 0.0
        val start = (progressIndex - 3).coerceAtLeast(0)
        val end = (progressIndex + 120).coerceAtMost(r.points.size - 2)
        for (i in start..end) {
            val a = r.points[i]; val b = r.points[i+1]
            val scale = cos(Math.toRadians(fix.latitude))
            val ax = (a.longitude - fix.longitude) * scale * 111320
            val ay = (a.latitude - fix.latitude) * 111320
            val dx = (b.longitude - a.longitude) * scale * 111320
            val dy = (b.latitude - a.latitude) * 111320
            val fraction = if (dx*dx + dy*dy < 0.01) 0.0 else (-(ax*dx + ay*dy)/(dx*dx + dy*dy)).coerceIn(0.0,1.0)
            val distance = hypot(ax + dx*fraction, ay + dy*fraction)
            if (distance < bestDistance) { bestDistance = distance; bestIndex = i; bestFraction = fraction }
        }
        if (bestDistance > max(60.0, fix.accuracy * 2.0)) {
            speedLimit = null
            offRouteSamples++
            dashRoute = dashRoute?.copy(remainingMillis=null)
            guidance = "Off route. Recalculating"
            if(offRouteSamples >= 3 && SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos < 10_000_000_000L) announce(announcements.offRoute())
            if (!routeBusy && offRouteSamples >= 3 && SystemClock.elapsedRealtime() - lastReroute > 30000) {
                lastReroute = SystemClock.elapsedRealtime(); context?.let { navigate(it, r.destination) }
            }
            return
        }
        offRouteSamples = 0; progressIndex = bestIndex; announcements.onTrack()
        val travelled = r.cumulative[bestIndex] + bestFraction*(r.cumulative[bestIndex+1]-r.cumulative[bestIndex])
        travelledMeters=travelled
        remaining = max(0.0, r.cumulative.last() - travelled)
        val segment=if(bestFraction >= 0.999) bestIndex+1 else bestIndex
        val headingDifference=reliableHeading(fix)?.let { abs(((it-routeBearing(r,bestIndex)+540)%360)-180) } ?: 0.0
        val fresh=SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos < 10_000_000_000L
        speedLimit=if(remaining > 10 && RoadSpeedLimits.visible(fresh,routeBusy,fix.accuracy,bestDistance,headingDifference)) speedWindow?.at(segment) else null
        if(!routeBusy) refreshSpeedLimits(r)
        val next = r.turns.firstOrNull { it.start > bestIndex } ?: r.turns.lastOrNull()
        maneuver = next?.sign ?: 0
        val meters = if (next == null) remaining else max(0.0, r.cumulative[next.start] - travelled)
        val turns = dashRoute?.takeIf { it.revision == dashRouteRevision }?.turns ?: r.turns.mapIndexed { index,it ->
            val previous = r.turns.getOrNull(index-1)?.start ?: 0
            DashTurn(it.sign, max(0.0,r.cumulative[it.start]-r.cumulative[previous]), it.text, it.road)
        }
        val duration = if(routeBusy) null else RouteTimeEstimator.remainingMillis(r.cumulative.last(),r.millis,travelled,r.timeLegs)
        dashRoute = DashRoute(dashRouteRevision,turns,r.turns.indexOf(next).coerceAtLeast(0),meters,duration)
        guidance = if (remaining < 25 && bestIndex >= r.points.size - 4) "Arriving at destination" else "${distance(meters)}  ${next?.text ?: "Continue"}"
        val nextIndex = r.turns.indexOf(next)
        val following = r.turns.getOrNull(nextIndex + 1)?.takeIf {
            next != null && it.start > next.start && r.cumulative[it.start] - r.cumulative[next.start] <= 120 && meters <= 120
        }?.let { NavigationSpeech.following(it.sign,it.road) }.orEmpty()
        if(!routeBusy && fresh) {
            val speed=reliableSpeed(fix) ?: 0.0
            val cue=announcements.next(next?.start ?: r.points.lastIndex,maneuver,meters,next?.road.orEmpty(),speed,
                remaining < 25 && bestIndex >= r.points.size - 4,SystemClock.elapsedRealtime(),following)
            announce(cue)
            // Direction prompts take priority. Delay reports wait for room before the next turn.
            if(cue==null && meters>max(150.0,speed*15) && !announceIncident()) announceTraffic()
        }
    }
    private fun announce(cue: NavigationAnnouncements.Cue?) {
        if(cue == null || !speechReady || context?.let { RidePreferences.prefs(it).getBoolean("navigation_voice",true) } != true) return
        if(cue.stage==0 && speaker?.isSpeaking==true) return
        if(speaker?.voice?.locale?.language?.let(NavigationSpeech::acceptsVoice) != true) return
        if(speaker?.speak(cue.text,TextToSpeech.QUEUE_FLUSH,null,"$routeGeneration:${cue.id}") == TextToSpeech.SUCCESS)
            announcements.accepted(cue,SystemClock.elapsedRealtime())
    }
    private fun announceTraffic() {
        val now=SystemClock.elapsedRealtime()
        val cue=trafficAnnouncements.next(now) ?: return
        if(!speechReady || speaker?.isSpeaking!=false || context?.let { RidePreferences.prefs(it).getBoolean("navigation_voice",true) } != true) return
        if(speaker?.voice?.locale?.language?.let(NavigationSpeech::acceptsVoice) != true) return
        if(speaker?.speak(cue.text,TextToSpeech.QUEUE_FLUSH,null,"$routeGeneration:traffic-delay") == TextToSpeech.SUCCESS)
            trafficAnnouncements.accepted(cue,now)
    }
    @JvmStatic fun currentTrafficAlert():TrafficAlert? {
        val c=context ?: return null;val fix=location ?: return null
        val age=incidentCheckedAt?.let{SystemClock.elapsedRealtime()-it} ?: return null
        if(!running || route==null || routeBusy || offRouteSamples>0 || age !in 0..360000 || incidentRevision!=dashRouteRevision ||
            SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos>=10_000_000_000L || !RidePreferences.prefs(c).getBoolean("tomtom_incident_alerts",true))return null
        val match=incidentMatches.firstOrNull{it.endMeters>=travelledMeters-20 && it.startMeters-travelledMeters<=3000} ?: return null
        return TrafficAlert(match.incident.id,match.incident.kind,(match.startMeters-travelledMeters).coerceAtLeast(0.0))
    }
    private fun announceIncident():Boolean {
        val alert=currentTrafficAlert() ?: return false;val now=SystemClock.elapsedRealtime()
        if(!incidentAnnouncements.next(alert,now) || !speechReady || speaker?.isSpeaking!=false ||
            context?.let{RidePreferences.prefs(it).getBoolean("navigation_voice",true)}!=true ||
            speaker?.voice?.locale?.language?.let(NavigationSpeech::acceptsVoice)!=true)return false
        if(speaker?.speak(alert.speech,TextToSpeech.QUEUE_FLUSH,null,"$routeGeneration:incident")!=TextToSpeech.SUCCESS)return false
        incidentAnnouncements.accepted(alert,now);return true
    }
    private fun refreshIncidents(c:Context,r:NavigationRoute) {
        if(incidentBusy || routeBusy || trafficRefreshBusy || !TomTomApi.configured(c) || !RidePreferences.prefs(c).getBoolean("tomtom_incident_alerts",true))return
        val now=SystemClock.elapsedRealtime()
        val due=if(incidentRequestedRevision!=dashRouteRevision)60000 else 300000
        if(now-lastIncidentRequest<due)return
        val revision=dashRouteRevision;val travelled=travelledMeters
        val points=r.points.map{IncidentPoint(it.latitude,it.longitude)}
        val indices=r.cumulative.indices.filter{r.cumulative[it]>=travelled && r.cumulative[it]<=travelled+10000}
        val start=(indices.firstOrNull() ?: progressIndex).minus(1).coerceAtLeast(0)
        val end=((indices.lastOrNull() ?: progressIndex)+1).coerceAtMost(points.lastIndex)
        if(end<=start)return
        incidentBusy=true;lastIncidentRequest=now;incidentRequestedRevision=revision;incidentStatus="Checking reported accidents and closures near the route ahead."
        incidentWorker.execute {
            val result=runCatching{RouteIncidentMatcher.match(points,r.cumulative,travelled,TomTomApi.routeIncidents(c,points.subList(start,end+1)))}
            main.post {
                incidentBusy=false
                if(!running || route!==r || revision!=dashRouteRevision)return@post
                result.onSuccess { matches ->
                    incidentMatches=matches;incidentRevision=revision;incidentCheckedAt=SystemClock.elapsedRealtime();invalidateFrames()
                    incidentStatus=if(matches.isEmpty()) "No current reports matched the route corridor. Coverage may be incomplete." else "${matches.size} reports matched near the route ahead. Lane and direction are unverified."
                    BikeDiagnostics.record(c,"Route incident check completed matched=${matches.size}")
                    // Ask the traffic-aware provider to check the route, rather than inventing an avoidance edge.
                    val closure=matches.firstOrNull{it.incident.kind==IncidentKind.ROAD_CLOSED && it.endMeters>=travelledMeters && it.startMeters-travelledMeters<=5000}
                    val checked=SystemClock.elapsedRealtime()
                    if(r.provider=="TomTom" && closure!=null && checked-lastClosureCheck>=60000 && checked-(checkedClosures[closure.incident.id] ?: -300000L)>=300000 && !routeBusy && !trafficRefreshBusy){
                        checkedClosures[closure.incident.id]=checked;lastClosureCheck=checked;lastTrafficRefresh=checked;refreshTrafficRoute(c,r)
                    }
                }.onFailure { incidentStatus="Incident alerts unavailable. Check internet and TomTom Traffic API access. Existing route remains available.";BikeDiagnostics.record(c,"Route incident check unavailable exception=${it.javaClass.simpleName}") }
            }
        }
    }
    private fun refreshTrafficRoute(c:Context,current:NavigationRoute) {
        if(trafficRefreshBusy) return
        val origin=location?.let(::Location) ?: return
        val generation=routeGeneration;trafficRefreshBusy=true
        worker.execute {
            val result=runCatching { NavigationApi.route(c,origin,current.destination) }
            main.post {
                trafficRefreshBusy=false
                if(!running || routeGeneration!=generation || route!==current) return@post
                result.onSuccess { routeGeneration++;applyRoute(c,it,false);BikeDiagnostics.record(c,"Traffic route refreshed provider=${it.provider}") }
                    .onFailure { BikeDiagnostics.record(c,"Traffic route refresh unavailable; retained current route exception=${it.javaClass.simpleName}") }
            }
        }
    }
    private fun refreshSpeedLimits(r: NavigationRoute) {
        if(r.provider=="TomTom") return
        val window=speedWindow
        if(window != null && progressIndex >= window.start &&
            (progressIndex < window.end-20 || window.end == r.points.lastIndex)) return
        val now=SystemClock.elapsedRealtime()
        if(speedRequestBusy || now-lastSpeedRequest < 30000) return
        val start=(progressIndex-3).coerceAtLeast(0)
        var end=start+1
        while(end < r.points.lastIndex && end-start < 400 && r.cumulative[end]-r.cumulative[start] < 3000) end++
        val generation=routeGeneration
        speedRequestBusy=true; lastSpeedRequest=now
        speedWorker.execute {
            val result=runCatching { NavigationApi.speedLimits(r,start,end) }
            main.post {
                speedRequestBusy=false
                if(!running || route !== r || routeGeneration != generation) return@post
                result.onSuccess {
                    speedWindow=it; location?.let(::updateGuidance)
                    context?.let { c -> BikeDiagnostics.record(c,"Speed-limit window ready segments=${it.limits.size} mapped=${it.limits.count { value -> value > 0 }}") }
                }.onFailure { error ->
                    context?.let { c -> BikeDiagnostics.record(c,"Speed-limit lookup unavailable exception=${error.javaClass.simpleName}") }
                }
            }
        }
    }
    private fun distance(meters: Double) = if (meters >= 1000) String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000) else "${(meters / 10).roundToInt() * 10} m"
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val c = context ?: return
            val fix = location
            val fresh = fix != null && SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos < 10_000_000_000L
            val activeRoute=route
            if(fresh && !routeBusy && activeRoute?.provider=="TomTom" &&
                RidePreferences.prefs(c).getBoolean("tomtom_refresh",true) && SystemClock.elapsedRealtime()-lastTrafficRefresh >= 300000) {
                lastTrafficRefresh=SystemClock.elapsedRealtime();refreshTrafficRoute(c,activeRoute)
            }
            if(fresh && activeRoute!=null && !routeBusy && offRouteSamples==0)refreshIncidents(c,activeRoute)
            if (!fresh) { status = if (fix == null) acquisitionStatus else "GPS signal lost. Waiting for a fresh fix"; guidance = "Waiting for GPS"; if(route != null && fix != null) announce(announcements.gpsLost()) }
            if (inFlight && SystemClock.elapsedRealtime() - renderStarted > 15000) {
                snapshotter?.cancel(); snapshotter = null; inFlight = false
                BikeDiagnostics.record(c, "Native map render timeout")
            }
            if (phoneInFlight && SystemClock.elapsedRealtime() - phoneRenderStarted > 15000) {
                phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false
            }
            if (fresh && !inFlight && SystemClock.elapsedRealtime() - frameAt > 400) render(c, fix!!)
            if (fresh && phoneVisible && !phoneInFlight && SystemClock.elapsedRealtime() - phoneFrameAt > 400) render(c, fix!!, true)
            if (SystemClock.elapsedRealtime() - lastSummary > 10000) {
                lastSummary = SystemClock.elapsedRealtime()
                BikeDiagnostics.record(c, "Native map status gpsFresh=$fresh route=${route != null} renderBusy=$inFlight frameAgeMs=${if(frameAt==0L)-1 else lastSummary-frameAt}")
            }
            main.postDelayed(this, 250)
        }
    }
    private fun activateMapStyle(c: Context, json: String, dark: Boolean) {
        if(styleDark == dark && englishStyle != null) return
        englishStyle=json; styleDark=dark
        snapshotter?.cancel(); snapshotter=null; inFlight=false
        phoneSnapshotter?.cancel(); phoneSnapshotter=null; phoneInFlight=false
        BikeDiagnostics.record(c,"Map sun mode=${if(dark) "night" else "day"}")
    }
    private fun loadMapStyle(c: Context, dark: Boolean) {
        if(styleLoading || SystemClock.elapsedRealtime() < styleRetryAt || styleCache.containsKey(dark)) return
        val generation=styleGeneration
        styleLoading=true
        styleWorker.execute {
            val result=runCatching { NavigationApi.englishMapStyle(c,dark) }
            main.post {
                if(!running || generation != styleGeneration) return@post
                styleLoading=false
                result.onSuccess {
                    styleCache[dark]=it
                    if(dark == isNightMap()) activateMapStyle(c,it,dark)
                    // Warm the other style while the current map remains usable.
                    loadMapStyle(c,!dark)
                }.onFailure {
                    styleRetryAt=SystemClock.elapsedRealtime()+15000
                    if(englishStyle == null) status="Map unavailable. Check provider key and internet connection"
                    BikeDiagnostics.record(c,"Map style unavailable dark=$dark exception=${it.javaClass.simpleName}")
                }
            }
        }
    }
    private fun render(c: Context, fix: Location, phone: Boolean = false) {
        val key = NavigationSecrets.read(c, "maptiler")
        if (key.isBlank()) { status = "Add your map provider key in Setup"; return }
        val night=isNightMap()
        if(styleDark != night) {
            styleCache[night]?.let { activateMapStyle(c,it,night) } ?: loadMapStyle(c,night)
        }
        val style=englishStyle ?: return
        val frameNight=styleDark == true
        val renderHeight = if (phone) phoneHeight else height
        val bikeLayout=BikeNavigationLayout(height)
        val mapWidth=if(phone) 480 else bikeLayout.mapWidth
        val mapHeight=if(phone) renderHeight else bikeLayout.mapHeight
        if (phone) { phoneInFlight = true; phoneRenderStarted = SystemClock.elapsedRealtime() }
        else { inFlight = true; renderStarted = SystemClock.elapsedRealtime() }
        try {
            val activeRoute = route
            val requestedZoom = mapZoom.state()
            val heading = reliableHeading(fix) ?: if (activeRoute != null) routeBearing(activeRoute, progressIndex) else 0.0
            val cameraBuilder = CameraPosition.Builder()
                .target(LatLng(fix.latitude, fix.longitude))
                .zoom(requestedZoom.level.toDouble())
                .bearing(heading)
            if (activeRoute != null) cameraBuilder.tilt(55.0)
            val camera = cameraBuilder.build()
            val renderer = (if (phone) phoneSnapshotter else snapshotter) ?: MapSnapshotter(c, MapSnapshotter.Options(mapWidth, mapHeight).withPixelRatio(1f)
                // RideDeck supplies visible MapTiler/OSM credits on the phone and each bike frame.
                .withLogo(false).withAttribution(false)
                .withStyleBuilder(Style.Builder().fromJson(style))
                .withCameraPosition(camera))
            if (phone) phoneSnapshotter = renderer else snapshotter = renderer
            renderer.setCameraPosition(camera)
            renderer.start({ snapshot ->
                if (!running || (if (phone) phoneSnapshotter else snapshotter) !== renderer) return@start
                if (mapZoom.state().revision != requestedZoom.revision) {
                    if (phone) phoneInFlight = false else inFlight = false
                    main.post { if (running) location?.let { render(c, it, phone) } }
                    return@start
                }
                val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.save(); canvas.clipRect(0, 0, mapWidth, mapHeight)
                activeRoute?.let { r ->
                    val path = Path()
                    r.points.drop((progressIndex-1).coerceAtLeast(0)).forEachIndexed { i, point ->
                        val pixel = snapshot.pixelForLatLng(point)
                        if (i==0) path.moveTo(pixel.x,pixel.y) else path.lineTo(pixel.x,pixel.y)
                    }
                    paint.color = Color.WHITE; paint.style=Paint.Style.STROKE; paint.strokeWidth=8f; canvas.drawPath(path,paint)
                    paint.color=if(phone) { if(frameNight) 0xff48a4ff.toInt() else 0xff087eff.toInt() } else 0xffdb35e7.toInt()
                    paint.strokeWidth=5f; canvas.drawPath(path,paint)
                }
                val pixel = snapshot.pixelForLatLng(LatLng(fix.latitude,fix.longitude))
                val riderHeading = reliableHeading(fix) ?: heading
                YamahaPositionMarker.draw(c,canvas,pixel.x,pixel.y,(riderHeading-camera.bearing).toFloat())
                canvas.restore()
                if (phone) {
                    if(renderHeight >= 164) currentSpeedLimitKph()?.let { SpeedLimitIcon.draw(canvas,42f,76f,30f,it) }
                    currentTrafficAlert()?.let { TrafficAlertRenderer.draw(canvas,it,84f,38f,388f) }
                    phoneMapBitmap = bitmap; phoneFrameAt = SystemClock.elapsedRealtime(); phoneInFlight = false
                } else {
                    val output=Bitmap.createBitmap(480,renderHeight,Bitmap.Config.ARGB_8888)
                    BikeNavigationRenderer.draw(Canvas(output),bitmap,bikeLayout,bikeState(),frameNight)
                    mapBitmap=output; mapJpeg=jpeg(output); renderedZoomRevision=requestedZoom.revision; frameAt=SystemClock.elapsedRealtime(); inFlight=false
                }
            }) { _ ->
                if ((if (phone) phoneSnapshotter else snapshotter) === renderer) { if (phone) { phoneInFlight=false; phoneSnapshotter=null } else { inFlight=false; snapshotter=null }; status="Map unavailable. Check provider key and internet connection"; BikeDiagnostics.record(c,"Native map render failed") }
            }
        } catch (e: Exception) { if (phone) { phoneInFlight=false; phoneSnapshotter=null } else { inFlight=false; snapshotter=null }; status="Map rendering unavailable"; BikeDiagnostics.record(c,"Native map render exception=${e.javaClass.simpleName}") }
    }
    private fun bikeState(): BikeNavigationState {
        val nav=dashRoute
        val fix=location
        val fresh=fix != null && SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos < 10_000_000_000L
        val next=nav?.turns?.getOrNull(nav.activeIndex)
        val currentRoad=route?.turns?.lastOrNull { it.start <= progressIndex && it.road.isNotBlank() }?.road.orEmpty()
        val arrival=cockpitTrip()?.arrivalMillis?.let { java.text.SimpleDateFormat("HH:mm",java.util.Locale.US).format(java.util.Date(it)) }
        val notice=when { !fresh -> "Waiting for GPS"; routeBusy || offRouteSamples > 0 -> "Recalculating"; route == null -> "Choose destination"; else -> "" }
        return BikeNavigationState(next?.sign ?: 0,nav?.nextMeters?.takeIf { notice.isBlank() },next?.road.orEmpty(),currentRoad,arrival,currentSpeedLimitKph(),notice,currentTrafficAlert())
    }
    private fun banner(canvas: Canvas, paint: Paint, text: String, top: Int, bottom: Int, size: Float, dark: Boolean = isNightMap()) {
        paint.style=Paint.Style.FILL; paint.color=if(dark) 0xff14251c.toInt() else 0xfff4f6fa.toInt(); canvas.drawRect(0f,top.toFloat(),480f,bottom.toFloat(),paint)
        paint.color=if(dark) Color.WHITE else 0xff14251c.toInt(); paint.textSize=size; paint.typeface=Typeface.DEFAULT_BOLD
        val shown=android.text.TextUtils.ellipsize(text,android.text.TextPaint(paint),466f,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(shown,7f,top+(bottom-top)/2f-(paint.ascent()+paint.descent())/2,paint)
    }
    private fun routeBearing(r: NavigationRoute, index: Int): Double {
        val from = r.points[index.coerceIn(0, r.points.lastIndex)]
        val to = r.points[(index + 1).coerceAtMost(r.points.lastIndex)]
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0
    }
    private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,85,it); it.toByteArray() }
    @JvmStatic fun frame(mode: String): ByteArray? = renderFrame(mode,false)
    private fun renderFrame(mode: String, phone: Boolean): ByteArray? {
        if (!phone && mode == "map" && mapJpeg != null && SystemClock.elapsedRealtime()-frameAt < 10000) return mapJpeg
        val bitmap=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
        val night=isNightMap()
        val canvas=Canvas(bitmap); canvas.drawColor(if(night) 0xff151c17.toInt() else 0xfff4f6fa.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(mode=="arrows" && route!=null) {
            banner(canvas,paint,guidance,0,40,20f)
            paint.color=if(night) 0xffb5ff76.toInt() else 0xff14251c.toInt(); paint.textSize=112f; paint.typeface=Typeface.DEFAULT_BOLD
            val arrow=when { maneuver == -8 -> "↶"; maneuver == 8 -> "↷"; maneuver==6 -> "↻"; maneuver==5 -> "●"; maneuver<0 -> "←"; maneuver in 1..3 || maneuver==7 -> "→"; else -> "↑" }
            canvas.drawText(arrow,205f,167f,paint)
            if(!phone) banner(canvas,paint,"${distance(remaining)}  |  ${phoneTripSummary()}",height-34,height,18f)
            currentSpeedLimitKph()?.let { SpeedLimitIcon.draw(canvas,42f,76f,30f,it) }
        } else {
            banner(canvas,paint,if(mode=="map") status else guidance,0,48,22f)
            paint.color=if(night) Color.WHITE else 0xff14251c.toInt(); paint.textSize=20f
            canvas.drawText(if(running) "Choose a destination on your phone" else "Open navigation on your phone",18f,125f,paint)
        }
        return jpeg(bitmap)
    }
    fun phoneBitmap(mode: String): Bitmap? = if(mode=="map") {
        if(phoneMapBitmap != null && SystemClock.elapsedRealtime()-phoneFrameAt<10000) phoneMapBitmap else renderFrame("map",true)?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
    } else renderFrame(mode,true)?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
}

class NativeNavigationService : Service(), LocationListener {
    private var locations: LocationManager? = null
    private var wake: RideSessionWakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="STOP") { RideBackgroundSession.navigationStopped(this); stopSelf(); return START_NOT_STICKY }
        if(intent == null && !RideBackgroundSession.navigationActive(this)) { stopSelf(); return START_NOT_STICKY }
        if(NativeNavigation.running) return START_STICKY
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { RideBackgroundSession.navigationStopped(this); stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("navigation","RideDeck navigation",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,NativeNavigationService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"navigation").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("RideDeck navigation")
            .setContentText("GPS navigation active").setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(23,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(23,notification)
            RideBackgroundSession.navigationStarted(this)
            wake=RideSessionWakeLock(this,"navigation").also { it.start() }
            locations=getSystemService(LocationManager::class.java)
            NativeNavigation.attach(this)
            RideBackgroundSession.destination(this)?.let { NativeNavigation.navigate(this,it) }
            val available = locations!!.allProviders
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { it in available }
            var registered = 0
            providers.forEach { provider ->
                try {
                    // Zero distance also delivers fixes while the rider is stationary.
                    if(Build.VERSION.SDK_INT>=31) {
                        val request=LocationRequest.Builder(if(provider==LocationManager.GPS_PROVIDER) 500L else 2000L)
                            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setMinUpdateIntervalMillis(250L).setMaxUpdateDelayMillis(0L).build()
                        locations!!.requestLocationUpdates(provider,request,mainExecutor,this)
                    } else locations!!.requestLocationUpdates(provider,500L,0f,this,Looper.getMainLooper())
                    registered++
                } catch (e: Exception) {
                    BikeDiagnostics.record(this, "Native location registration failed provider=$provider exception=${e.javaClass.simpleName}")
                }
            }
            if (registered == 0) {
                NativeNavigation.acquisitionStatus = "Location provider unavailable on this phone"
            } else {
                refreshProviderStatus()
                providers.mapNotNull { provider ->
                    runCatching { locations!!.getLastKnownLocation(provider) }.getOrNull()
                }.sortedBy { it.elapsedRealtimeNanos }.forEach { NativeNavigation.update(it) }
            }
        } catch(e: Exception) { BikeDiagnostics.record(this,"Native GPS start failed exception=${e.javaClass.simpleName}"); RideBackgroundSession.navigationStopped(this); stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onLocationChanged(location: Location) { NativeNavigation.update(location) }
    private fun refreshProviderStatus() {
        val enabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any {
            runCatching { locations?.isProviderEnabled(it) == true }.getOrDefault(false)
        }
        NativeNavigation.acquisitionStatus = if (enabled) "Waiting for a GPS fix. Move outdoors if needed"
            else "Enable phone location to continue navigation"
        if (NativeNavigation.location == null) NativeNavigation.status = NativeNavigation.acquisitionStatus
    }
    override fun onProviderDisabled(provider: String) { refreshProviderStatus() }
    override fun onProviderEnabled(provider: String) { refreshProviderStatus() }
    override fun onTaskRemoved(rootIntent: Intent?) { BikeDiagnostics.record(this,"Navigation continues after Recent apps removal"); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        try { locations?.removeUpdates(this) } catch(_: Exception) { }
        wake?.close(); wake=null; NativeNavigation.detach()
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
