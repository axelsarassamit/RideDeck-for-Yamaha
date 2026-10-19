package com.axelsarassamit.gx12

import android.content.Context
import android.location.Location
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

/** REST services use a phone-stored key. SDK trial entitlements are independent. */
object TomTomApi {
    @Volatile private var extendedLanesUnavailable=false
    fun configured(c:Context)=NavigationSecrets.read(c,"tomtom").isNotBlank()
    private fun endpoint(c:Context,path:String):Uri.Builder {
        val key=NavigationSecrets.read(c,"tomtom")
        check(key.isNotBlank()) { "Add a TomTom key in Map and routing accounts." }
        return Uri.parse("https://api.tomtom.com/$path").buildUpon().appendQueryParameter("key",key)
    }
    fun search(c:Context,query:String,fix:Location?):List<NavigationPlace> {
        val all=mutableListOf<NavigationPlace>()
        for(q in app.pillion.core.DestinationInput.queries(query)) {
            val url=endpoint(c,"search/2/search/${Uri.encode(q.take(300))}.json")
                .appendQueryParameter("limit","10").appendQueryParameter("language","en-GB").appendQueryParameter("typeahead","false")
            if(fix!=null && android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos in 0..60_000_000_000L)
                url.appendQueryParameter("lat","${fix.latitude}").appendQueryParameter("lon","${fix.longitude}")
            val results=NavigationApi.request(url.build(),"TomTom").getJSONArray("results")
            for(i in 0 until results.length()) {
                val row=results.getJSONObject(i);val p=row.optJSONObject("position") ?: continue
                val lat=p.optDouble("lat");val lon=p.optDouble("lon");if(lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val address=row.optJSONObject("address")?.optString("freeformAddress").orEmpty()
                val name=row.optJSONObject("poi")?.optString("name").orEmpty()
                val kind=when(row.optString("type")) { "POI"->"poi";"Point Address","Address Range"->"address";"Street"->"road";else->"area" }
                all.add(NavigationPlace(listOf(name,address).filter { it.isNotBlank() }.distinct().joinToString("\n"),lat,lon,kind,1.0,"TomTom"))
            }
            if(all.any { it.kind in listOf("poi","address") }) break
        }
        return all.distinctBy { "${it.latitude},${it.longitude}" }.take(10)
    }
    fun routes(c:Context,origin:Location,target:NavigationPlace,alternatives:Boolean,via:List<NavigationPlace> = emptyList(),style:String = "relaxed"):List<NavigationRoute> {
        val preferences=RoutePreferencesUi.read(c)
        val coordinates=(listOf(NavigationPlace("Start",origin.latitude,origin.longitude))+via+target).joinToString(":"){"${it.latitude},${it.longitude}"}
        val url=endpoint(c,"routing/1/calculateRoute/$coordinates/json")
            .appendQueryParameter("travelMode","motorcycle").appendQueryParameter("traffic","true").appendQueryParameter("departAt","now")

            .appendQueryParameter("routeType",if(style=="winding") "thrilling" else if(preferences.shortest) "shortest" else "fastest").appendQueryParameter("alternativeType","anyRoute")
            .appendQueryParameter("maxAlternatives",if(alternatives) "2" else "0")
            .appendQueryParameter("instructionsType","text").appendQueryParameter("language","en-GB")
        NativeNavigation.reliableHeading(origin)?.takeIf { android.os.SystemClock.elapsedRealtimeNanos()-origin.elapsedRealtimeNanos in 0..5_000_000_000L }?.let {
            url.appendQueryParameter("vehicleHeading","${kotlin.math.round(it).toInt()%360}")
        }
        for(avoid in preferences.tomTomAvoids())url.appendQueryParameter("avoid",avoid)
        for(section in listOf("speedLimit","traffic","motorway","toll","tollVignette","travelMode","ferry","unpaved")) url.appendQueryParameter("sectionType",section)
        if(style=="winding") { url.appendQueryParameter("windingness","high");url.appendQueryParameter("hilliness","normal") }
        val base=url.build()
        val lanes=preferences.laneGuidance && !extendedLanesUnavailable
        val request=if(lanes)base.buildUpon().appendQueryParameter("sectionType","lanes").build() else base
        val response=try { NavigationApi.request(request,"TomTom") } catch(e:IllegalStateException) {
            if(!lanes || ("HTTP 400" !in e.message.orEmpty() && "HTTP 403" !in e.message.orEmpty()))throw e
            extendedLanesUnavailable=true
            BikeDiagnostics.record(c,"TomTom extended lane data unavailable; retrying standard motorcycle route")
            NavigationApi.request(base,"TomTom")
        }
        return RoutePreferencesUi.ranked(parseRoutes(response,target,preferences),preferences)
    }
    internal fun parseRoutes(json:JSONObject,target:NavigationPlace,preferences:app.pillion.core.RoadPreferences=app.pillion.core.RoadPreferences()):List<NavigationRoute> {
        val result=mutableListOf<NavigationRoute>();val raw=json.getJSONArray("routes")
        for(i in 0 until minOf(raw.length(),3)) {
            val r=raw.getJSONObject(i);val sections=r.optJSONArray("sections")
            // Avoid is a routing preference. Reject a returned motorway/toll route rather than treating it as a guarantee.
            if((0 until (sections?.length() ?: 0)).any {
                when(sections!!.getJSONObject(it).optString("sectionType")) {
                    "MOTORWAY"->preferences.avoidMotorways
                    "TOLL_ROAD","TOLL","TOLL_VIGNETTE"->preferences.avoidTolls
                    "FERRY"->preferences.avoidFerries
                    "UNPAVED"->preferences.avoidDirt
                    else->false
                }
            }) continue
            val legs=r.getJSONArray("legs");require(legs.length() in 1..6) { "Unexpected motorcycle route." }
            var geometry=JSONArray();val stopIndices=mutableListOf<Int>();val rawGeometry=JSONArray();val rawEnds=mutableListOf<Int>()
            for(leg in 0 until legs.length()) {
                val rows=legs.getJSONObject(leg).getJSONArray("points");require(rows.length()>=2)
                for(n in 0 until rows.length()) {
                    val point=rows.getJSONObject(n);rawGeometry.put(point)
                    if(n==0 && geometry.length()>0 && geometry.getJSONObject(geometry.length()-1).getDouble("latitude")==point.getDouble("latitude") && geometry.getJSONObject(geometry.length()-1).getDouble("longitude")==point.getDouble("longitude")) continue
                    geometry.put(point)
                };stopIndices.add(geometry.length()-1);rawEnds.add(rawGeometry.length()-1)
            }
            if(legs.length()>1) {
                val guidance=r.getJSONObject("guidance").getJSONArray("instructions")
                val last=guidance.getJSONObject(guidance.length()-1)
                require(last.optString("maneuver").startsWith("ARRIVE")) { "Ride arrival guidance unavailable." }
                val lastIndex=last.getInt("pointIndex")
                if(lastIndex==rawGeometry.length()-1) {geometry=rawGeometry;stopIndices.clear();stopIndices.addAll(rawEnds)}
                else require(lastIndex==geometry.length()-1) { "Ride instruction geometry could not be aligned." }
            }
            require(geometry.length() in 2..100000)
            val points=(0 until geometry.length()).map { n->geometry.getJSONObject(n).let {
                val lat=it.getDouble("latitude");val lon=it.getDouble("longitude");require(lat in -90.0..90.0 && lon in -180.0..180.0);LatLng(lat,lon)
            } }
            val summary=r.getJSONObject("summary");val seconds=summary.getLong("travelTimeInSeconds")
            val meters=summary.getDouble("lengthInMeters");require(seconds in 0..31_536_000 && meters.isFinite() && meters>=0)
            val instructions=r.getJSONObject("guidance").getJSONArray("instructions");require(instructions.length() in 1..65535)
            val rows=(0 until instructions.length()).map { instructions.getJSONObject(it) }
            val turns=rows.mapIndexed { n,row->
                val start=row.getInt("pointIndex");val end=rows.getOrNull(n+1)?.getInt("pointIndex") ?: points.lastIndex
                require(start in points.indices && end in start..points.lastIndex)
                val begin=row.optLong("travelTimeInSeconds",0);val finish=rows.getOrNull(n+1)?.optLong("travelTimeInSeconds",seconds) ?: seconds
                val intermediateArrival=legs.length()>1 && n<rows.lastIndex && row.optString("maneuver").startsWith("ARRIVE")
                NavigationTurn(if(intermediateArrival) "Continue on the planned ride" else row.optString("message","Continue"),if(intermediateArrival) 0 else NavigationManeuver.tomTom(row.optString("maneuver"), row.optString("modifier"), row.optString("drivingSide")),start,end,row.optString("street"),(finish-begin).coerceAtLeast(0)*1000)
            }
            val limits=IntArray(points.size-1);val seen=BooleanArray(limits.size)
            for(n in 0 until (sections?.length() ?: 0)) {
                val s=sections!!.getJSONObject(n);if(s.optString("sectionType")!="SPEED_LIMIT") continue
                val a=s.optInt("startPointIndex",-1);val b=s.optInt("endPointIndex",-1)
                if(a<0 || b<=a || b>points.lastIndex) continue
                val limit=app.pillion.core.RoadSpeedLimits.numeric((s.opt("maxSpeedLimitInKmh") as? Number)?.toDouble()) ?: 0
                for(edge in a until b) { limits[edge]=if(seen[edge]) 0 else limit;seen[edge]=true }
            }
            result.add(NavigationRoute(target,points,turns,meters,seconds*1000,"TomTom",limits,summary.optLong("trafficDelayInSeconds",0).coerceAtLeast(0)*1000,LaneData.tomTom(sections,points.size),stopIndices=stopIndices))
        }
        check(result.isNotEmpty()) { "No motorcycle route matching the selected road preferences was found. Try changing Road preferences." }
        return result
    }
    fun stations(c:Context,lat:Double,lon:Double):List<BikePlace> {
        val json=NavigationApi.request(endpoint(c,"search/2/nearbySearch/.json").appendQueryParameter("lat","$lat")
            .appendQueryParameter("lon","$lon").appendQueryParameter("radius","10000").appendQueryParameter("limit","20")
            .appendQueryParameter("categorySet","7311").appendQueryParameter("language","en-GB").build(),"TomTom")
        val rows=json.getJSONArray("results")
        return (0 until rows.length()).mapNotNull { i->
            val row=rows.getJSONObject(i);val p=row.optJSONObject("position") ?: return@mapNotNull null
            val a=p.optDouble("lat");val b=p.optDouble("lon");if(a !in -90.0..90.0 || b !in -180.0..180.0) return@mapNotNull null
            val dist=FloatArray(1);Location.distanceBetween(lat,lon,a,b,dist)
            BikePlace(row.optJSONObject("poi")?.optString("name","Fuel station") ?: "Fuel station","$a,$b",dist[0],"TomTom",
                row.optJSONObject("dataSources")?.optJSONObject("fuelPrice")?.optString("id").orEmpty())
        }.filter { it.metres<=10000 }.sortedBy { it.metres }
    }
    fun fuelPrices(c:Context,id:String):List<FuelPrice> {
        check(RidePreferences.prefs(c).getBoolean("tomtom_fuel_entitled",false)) { "Fuel Prices API requires separate account access." }
        val json=NavigationApi.request(endpoint(c,"search/2/fuelPrice.json").appendQueryParameter("fuelPrice",id).build(),"TomTom fuel prices")
        val fuels=json.optJSONArray("fuels") ?: return emptyList()
        return (0 until fuels.length()).flatMap { i->
            val fuel=fuels.getJSONObject(i);val prices=fuel.optJSONArray("price")
            (0 until (prices?.length() ?: 0)).mapNotNull { n->
                val p=prices!!.getJSONObject(n);val value=p.optDouble("value");val currency=p.optString("currency");val unit=p.optString("volumeUnit")
                val updated=fuel.optString("updatedAt");val grade=fuel.optString("type")
                if(!value.isFinite() || value<=0 || currency.isBlank() || unit.isBlank() || updated.isBlank() || grade.isBlank()) null
                else FuelPrice(grade,value,currency,unit,updated)
            }
        }
    }
    fun incidents(c:Context,fix:Location):List<String> {
        val latSpan=.09;val lonSpan=.09/kotlin.math.cos(Math.toRadians(fix.latitude)).coerceAtLeast(.1)
        val bbox="${(fix.longitude-lonSpan).coerceAtLeast(-180.0)},${(fix.latitude-latSpan).coerceAtLeast(-90.0)},${(fix.longitude+lonSpan).coerceAtMost(180.0)},${(fix.latitude+latSpan).coerceAtMost(90.0)}"
        val json=NavigationApi.request(endpoint(c,"traffic/services/5/incidentDetails").appendQueryParameter("bbox",bbox)
            .appendQueryParameter("fields","{incidents{properties{iconCategory,from,to,delay,events{description}}}}")
            .appendQueryParameter("language","en-GB").appendQueryParameter("timeValidityFilter","present").build(),"TomTom traffic")
        val rows=json.optJSONArray("incidents") ?: return emptyList()
        return (0 until minOf(rows.length(),40)).map { i->
            val p=rows.getJSONObject(i).getJSONObject("properties");val events=p.optJSONArray("events")
            val details=(0 until (events?.length() ?: 0)).map { events!!.getJSONObject(it).optString("description") }.filter { it.isNotBlank() }.distinct().joinToString("; ")
            val road=listOf(p.optString("from"),p.optString("to")).filter { it.isNotBlank() }.distinct().joinToString(" to ")
            listOf(details,road).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Traffic incident reported" }
        }
    }
    fun routeIncidents(c:Context,points:List<IncidentPoint>):List<ReportedIncident> {
        require(points.isNotEmpty() && points.all{it.valid()})
        val pad=.005
        val bbox="${(points.minOf{it.longitude}-pad).coerceAtLeast(-180.0)},${(points.minOf{it.latitude}-pad).coerceAtLeast(-85.0)},${(points.maxOf{it.longitude}+pad).coerceAtMost(180.0)},${(points.maxOf{it.latitude}+pad).coerceAtMost(85.0)}"
        val url=endpoint(c,"traffic/services/5/incidentDetails").appendQueryParameter("bbox",bbox)
            .appendQueryParameter("fields","{incidents{geometry{type,coordinates},properties{id,iconCategory,timeValidity,probabilityOfOccurrence,startTime,endTime}}}")
            .appendQueryParameter("language","en-GB").appendQueryParameter("categoryFilter","Accident,RoadClosed")
            .appendQueryParameter("timeValidityFilter","present").build()
        return parseRouteIncidents(NavigationApi.request(url,"TomTom traffic"),System.currentTimeMillis())
    }
    internal fun parseRouteIncidents(json:JSONObject,now:Long):List<ReportedIncident> {
        val rows=json.optJSONArray("incidents") ?: return emptyList()
        return (0 until minOf(rows.length(),100)).mapNotNull { i -> runCatching {
            val row=rows.getJSONObject(i);val p=row.getJSONObject("properties")
            val id=p.optString("id").takeIf{it.isNotBlank() && it.length<=256} ?: return@runCatching null
            val kind=IncidentKind.fromCode(p.optInt("iconCategory",-1)) ?: return@runCatching null
            if(p.optString("timeValidity")!="present" || p.optString("probabilityOfOccurrence") !in listOf("certain","probable"))return@runCatching null
            for(field in listOf("startTime","endTime")){
                val value=p.optString(field).takeUnless{it.isBlank() || it=="null"} ?: continue
                val time=java.time.Instant.parse(value).toEpochMilli()
                if((field=="startTime" && time>now) || (field=="endTime" && time<=now))return@runCatching null
            }
            val geometry=row.getJSONObject("geometry");val raw=geometry.getJSONArray("coordinates")
            fun point(a:org.json.JSONArray):IncidentPoint {require(a.length()>=2);return IncidentPoint(a.getDouble(1),a.getDouble(0)).also{require(it.valid())}}
            val line=geometry.getString("type")=="LineString"
            val points=when(geometry.getString("type")){"Point"->listOf(point(raw));"LineString"->{require(raw.length() in 2..512);(0 until raw.length()).map{point(raw.getJSONArray(it))}};else->return@runCatching null}
            ReportedIncident(id,kind,points,line)
        }.getOrNull() }.distinctBy{it.id}
    }
}
data class FuelPrice(val grade:String,val value:Double,val currency:String,val unit:String,val updated:String)
