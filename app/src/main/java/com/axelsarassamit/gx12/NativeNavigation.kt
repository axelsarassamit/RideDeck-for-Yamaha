package com.axelsarassamit.gx12

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.*
import app.pillion.core.DashRoute
import app.pillion.core.DashTurn
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
    private var zoomLevel = 16.0
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
    private var styleLoading = false
    private var styleRetryAt = 0L
    private var inFlight = false
    private var frameAt = 0L
    private var renderStarted = 0L
    private var routeGeneration = 0
    private var dashRouteRevision = 0
    private var routeBusy = false
    private var pendingDestination: NavigationPlace? = null
    private var progressIndex = 0
    private var remaining = 0.0
    private var lastReroute = 0L
    private var offRouteSamples = 0
    private var lastSpoken = ""
    private var speaker: TextToSpeech? = null
    private var speechReady = false
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
        speaker = TextToSpeech(c) { result -> speechReady = result == TextToSpeech.SUCCESS }
        speaker?.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        main.removeCallbacks(tick); main.post(tick)
        BikeDiagnostics.record(c, "Native navigation started renderer=MapLibre locationService=true")
    }
    fun detach() {
        running = false; routeGeneration++; routeBusy = false; pendingDestination = null
        main.removeCallbacks(tick); snapshotter?.cancel(); snapshotter = null; inFlight = false
        speaker?.shutdown(); speaker = null; speechReady = false
        phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false; phoneMapBitmap = null; englishStyle = null; styleLoading = false
        location = null; mapJpeg = null; mapBitmap = null; dashRoute = null
        status = "Navigation stopped"
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
        if (!next.hasAccuracy() || next.accuracy > 50f || SystemClock.elapsedRealtimeNanos() - next.elapsedRealtimeNanos > 30_000_000_000L) {
            acquisitionStatus = "Waiting for a precise GPS fix"
            status = acquisitionStatus; return
        }
        val previous = location
        if (previous != null && next.elapsedRealtimeNanos < previous.elapsedRealtimeNanos) return
        location = Location(next)
        pendingDestination?.let { target ->
            pendingDestination = null
            context?.let { navigate(it, target) }
            return
        }
        if (route != null) status = "Navigating"
        else if (!routeBusy && !status.startsWith("Route unavailable:")) status = "GPS ready. Choose a destination"
        updateGuidance(next)
    }
    @JvmStatic fun stopRoute() {
        main.post { routeGeneration++; routeBusy = false; pendingDestination = null; route = null; dashRoute = null; guidance = "Choose a destination"; lastSpoken = ""; progressIndex = 0; speaker?.stop() }
    }
    @JvmStatic fun routeText(text: String) {
        val c = context ?: error("Open RideDeck navigation first")
        val coordinate = NavigationApi.coordinate(text)
        check(coordinate != null) { "Choose this address from search suggestions on the phone first. Save coordinates for bike favorites." }
        main.post { navigate(c, coordinate) }
    }
    fun navigate(c: Context, target: NavigationPlace) {
        context = c.applicationContext
        val origin = location
        if (origin == null || SystemClock.elapsedRealtimeNanos() - origin.elapsedRealtimeNanos > 30_000_000_000L) {
            pendingDestination = target
            status = "Waiting for GPS to calculate the route"; return
        }
        val generation = ++routeGeneration
        routeBusy = true; status = "Calculating route"
        worker.execute {
            val result = runCatching { NavigationApi.route(c, origin, target) }
            main.post {
                if (generation != routeGeneration) return@post
                routeBusy = false
                result.onSuccess {
                    route = it; dashRouteRevision++; dashRoute = null; progressIndex = 0; lastSpoken = ""; offRouteSamples = 0
                    status = "Navigating"; location?.let(::updateGuidance)
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
    @JvmStatic fun zoom(inside: Boolean) { main.post { zoomLevel = (zoomLevel + if (inside) 0.5 else -0.5).coerceIn(12.0, 19.0) } }

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
            offRouteSamples++
            guidance = "Off route. Recalculating"
            if (!routeBusy && offRouteSamples >= 3 && SystemClock.elapsedRealtime() - lastReroute > 30000) {
                lastReroute = SystemClock.elapsedRealtime(); context?.let { navigate(it, r.destination) }
            }
            return
        }
        offRouteSamples = 0; progressIndex = bestIndex
        val travelled = r.cumulative[bestIndex] + bestFraction*(r.cumulative[bestIndex+1]-r.cumulative[bestIndex])
        remaining = max(0.0, r.cumulative.last() - travelled)
        val next = r.turns.firstOrNull { it.start > bestIndex } ?: r.turns.lastOrNull()
        maneuver = next?.sign ?: 0
        val meters = if (next == null) remaining else max(0.0, r.cumulative[next.start] - travelled)
        val turns = dashRoute?.takeIf { it.revision == dashRouteRevision }?.turns ?: r.turns.mapIndexed { index,it ->
            val previous = r.turns.getOrNull(index-1)?.start ?: 0
            DashTurn(it.sign, max(0.0,r.cumulative[it.start]-r.cumulative[previous]), it.text, it.road)
        }
        dashRoute = DashRoute(dashRouteRevision,turns,r.turns.indexOf(next).coerceAtLeast(0),meters)
        guidance = if (remaining < 25 && bestIndex >= r.points.size - 4) "Arriving at destination" else "${distance(meters)}  ${next?.text ?: "Continue"}"
        val speechId = "${routeGeneration}:${next?.start}"
        if (meters < 200 && lastSpoken != speechId && speechReady && context?.let { RidePreferences.prefs(it).getBoolean("navigation_voice", true) } == true) {
            lastSpoken = speechId; speaker?.speak(guidance, TextToSpeech.QUEUE_FLUSH, null, speechId)
        }
    }
    private fun distance(meters: Double) = if (meters >= 1000) String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000) else "${(meters / 10).roundToInt() * 10} m"
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val c = context ?: return
            val fix = location
            val fresh = fix != null && SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos < 30_000_000_000L
            if (!fresh) { status = if (fix == null) acquisitionStatus else "GPS signal lost. Waiting for a fresh fix"; guidance = "Waiting for GPS" }
            if (inFlight && SystemClock.elapsedRealtime() - renderStarted > 15000) {
                snapshotter?.cancel(); snapshotter = null; inFlight = false
                BikeDiagnostics.record(c, "Native map render timeout")
            }
            if (phoneInFlight && SystemClock.elapsedRealtime() - phoneRenderStarted > 15000) {
                phoneSnapshotter?.cancel(); phoneSnapshotter = null; phoneInFlight = false
            }
            if (fresh && !inFlight && SystemClock.elapsedRealtime() - frameAt > 750) render(c, fix!!)
            if (fresh && phoneVisible && !phoneInFlight && SystemClock.elapsedRealtime() - phoneFrameAt > 750) render(c, fix!!, true)
            if (SystemClock.elapsedRealtime() - lastSummary > 10000) {
                lastSummary = SystemClock.elapsedRealtime()
                BikeDiagnostics.record(c, "Native map status gpsFresh=$fresh route=${route != null} renderBusy=$inFlight frameAgeMs=${if(frameAt==0L)-1 else lastSummary-frameAt}")
            }
            main.postDelayed(this, 500)
        }
    }
    private fun render(c: Context, fix: Location, phone: Boolean = false) {
        val key = NavigationSecrets.read(c, "maptiler")
        if (key.isBlank()) { status = "Add your map provider key in Setup"; return }
        val style = englishStyle
        if (style == null) {
            if (!styleLoading && SystemClock.elapsedRealtime() >= styleRetryAt) {
                styleLoading = true
                worker.execute {
                    val result = runCatching { NavigationApi.englishMapStyle(c) }
                    main.post {
                        styleLoading = false
                        if (!running) return@post
                        result.onSuccess { englishStyle = it }.onFailure {
                            styleRetryAt = SystemClock.elapsedRealtime() + 15000
                            status = "Map unavailable. Check provider key and internet connection"
                            BikeDiagnostics.record(c, "English map style failed exception=${it.javaClass.simpleName}")
                        }
                    }
                }
            }
            return
        }
        val renderHeight = if (phone) phoneHeight else height
        if (phone) { phoneInFlight = true; phoneRenderStarted = SystemClock.elapsedRealtime() }
        else { inFlight = true; renderStarted = SystemClock.elapsedRealtime() }
        try {
            val activeRoute = route
            val heading = if (fix.hasBearing() && fix.speed > 1f) fix.bearing.toDouble() else if (activeRoute != null) routeBearing(activeRoute, progressIndex) else 0.0
            val cameraBuilder = CameraPosition.Builder()
                .target(LatLng(fix.latitude, fix.longitude))
                .zoom(if (activeRoute != null) maxOf(zoomLevel, 17.0) else zoomLevel)
                .bearing(heading)
            if (activeRoute != null) cameraBuilder.tilt(55.0)
            val camera = cameraBuilder.build()
            val renderer = (if (phone) phoneSnapshotter else snapshotter) ?: MapSnapshotter(c, MapSnapshotter.Options(480, renderHeight).withPixelRatio(1f)
                .withStyleBuilder(Style.Builder().fromJson(style))
                .withCameraPosition(camera))
            if (phone) phoneSnapshotter = renderer else snapshotter = renderer
            renderer.setCameraPosition(camera)
            renderer.start({ snapshot ->
                if (!running || (if (phone) phoneSnapshotter else snapshotter) !== renderer) return@start
                val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.save(); canvas.clipRect(0, 34, 480, renderHeight - 20)
                activeRoute?.let { r ->
                    val path = Path()
                    r.points.drop((progressIndex-1).coerceAtLeast(0)).forEachIndexed { i, point ->
                        val pixel = snapshot.pixelForLatLng(point)
                        if (i==0) path.moveTo(pixel.x,pixel.y) else path.lineTo(pixel.x,pixel.y)
                    }
                    paint.color = Color.WHITE; paint.style=Paint.Style.STROKE; paint.strokeWidth=8f; canvas.drawPath(path,paint)
                    paint.color=0xff087eff.toInt(); paint.strokeWidth=5f; canvas.drawPath(path,paint)
                }
                val pixel = snapshot.pixelForLatLng(LatLng(fix.latitude,fix.longitude))
                paint.style=Paint.Style.FILL; paint.color=Color.WHITE; canvas.drawCircle(pixel.x,pixel.y,10f,paint)
                paint.color=0xff087eff.toInt(); canvas.drawCircle(pixel.x,pixel.y,7f,paint)
                canvas.restore()
                banner(canvas, paint, guidance, 0, 34, 19f)
                // Attribution remains visible on every streamed frame, including the small dashboard.
                paint.color=Color.WHITE; canvas.drawRect(0f,(renderHeight-20).toFloat(),480f,renderHeight.toFloat(),paint)
                val logo=c.getDrawable(R.drawable.maptiler_logo)!!
                logo.setBounds(3,renderHeight-20,70,renderHeight); logo.draw(canvas)
                paint.color=Color.BLACK; paint.textSize=11f; paint.typeface=Typeface.DEFAULT
                canvas.drawText("© MapTiler  © OpenStreetMap contributors",83f,renderHeight-6f,paint)
                if (phone) { phoneMapBitmap = bitmap; phoneFrameAt = SystemClock.elapsedRealtime(); phoneInFlight = false }
                else { mapBitmap=bitmap; mapJpeg=jpeg(bitmap); frameAt=SystemClock.elapsedRealtime(); inFlight=false }
            }) { _ ->
                if ((if (phone) phoneSnapshotter else snapshotter) === renderer) { if (phone) { phoneInFlight=false; phoneSnapshotter=null } else { inFlight=false; snapshotter=null }; status="Map unavailable. Check provider key and internet connection"; BikeDiagnostics.record(c,"Native map render failed") }
            }
        } catch (e: Exception) { if (phone) { phoneInFlight=false; phoneSnapshotter=null } else { inFlight=false; snapshotter=null }; status="Map rendering unavailable"; BikeDiagnostics.record(c,"Native map render exception=${e.javaClass.simpleName}") }
    }
    private fun banner(canvas: Canvas, paint: Paint, text: String, top: Int, bottom: Int, size: Float) {
        paint.style=Paint.Style.FILL; paint.color=0xff14251c.toInt(); canvas.drawRect(0f,top.toFloat(),480f,bottom.toFloat(),paint)
        paint.color=Color.WHITE; paint.textSize=size; paint.typeface=Typeface.DEFAULT_BOLD
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
    @JvmStatic fun frame(mode: String): ByteArray? {
        if (mode == "map" && mapJpeg != null && SystemClock.elapsedRealtime()-frameAt < 10000) return mapJpeg
        val bitmap=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(0xff151c17.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(mode=="arrows" && route!=null) {
            banner(canvas,paint,guidance,0,40,20f)
            paint.color=0xffb5ff76.toInt(); paint.textSize=112f; paint.typeface=Typeface.DEFAULT_BOLD
            val arrow=when { maneuver==6 -> "↻"; maneuver==5 -> "●"; maneuver<0 -> "←"; maneuver in 1..3 || maneuver==7 -> "→"; else -> "↑" }
            canvas.drawText(arrow,205f,167f,paint)
            banner(canvas,paint,"${distance(remaining)} remaining",height-34,height,18f)
        } else {
            banner(canvas,paint,if(mode=="map") status else guidance,0,48,22f)
            paint.color=Color.WHITE; paint.textSize=20f
            canvas.drawText(if(running) "Choose a destination on your phone" else "Open navigation on your phone",18f,125f,paint)
        }
        return jpeg(bitmap)
    }
    fun phoneBitmap(mode: String): Bitmap? = if(mode=="map") {
        if(phoneMapBitmap != null && SystemClock.elapsedRealtime()-phoneFrameAt<10000) phoneMapBitmap else if(mapBitmap != null && SystemClock.elapsedRealtime()-frameAt<10000) mapBitmap else frame("map")?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
    } else frame(mode)?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
}

class NativeNavigationService : Service(), LocationListener {
    private var locations: LocationManager? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="STOP") { stopSelf(); return START_NOT_STICKY }
        if(NativeNavigation.running) return START_NOT_STICKY
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("navigation","RideDeck navigation",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,NativeNavigationService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"navigation").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("RideDeck navigation")
            .setContentText("GPS navigation active").setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        if(Build.VERSION.SDK_INT>=29) startForeground(23,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(23,notification)
        try {
            locations=getSystemService(LocationManager::class.java)
            NativeNavigation.attach(this)
            val available = locations!!.allProviders
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { it in available }
            var registered = 0
            providers.forEach { provider ->
                try {
                    // Zero distance also delivers fixes while the rider is stationary.
                    locations!!.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper())
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
        } catch(e: Exception) { BikeDiagnostics.record(this,"Native GPS start failed exception=${e.javaClass.simpleName}"); stopSelf() }
        return START_NOT_STICKY
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
    override fun onDestroy() { locations?.removeUpdates(this); NativeNavigation.detach(); super.onDestroy() }
}
