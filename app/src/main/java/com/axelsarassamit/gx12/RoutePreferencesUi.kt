package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.*
import app.pillion.core.RoadPreferences

object RoutePreferencesUi {
    fun read(c:Context):RoadPreferences {
        val p=RidePreferences.prefs(c)
        return RoadPreferences(p.getBoolean("route_avoid_motorways",true),p.getBoolean("route_avoid_tolls",true),
            p.getBoolean("route_avoid_ferries",false),p.getBoolean("route_avoid_dirt",true),
            p.getBoolean("route_fewer_uturns",false),p.getBoolean("route_shortest",false),p.getBoolean("route_lanes",true))
    }
    fun show(a:Activity, saved:(()->Unit)?=null) {
        val p=read(a);val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        val options=listOf("Avoid motorways" to p.avoidMotorways,"Avoid toll roads" to p.avoidTolls,
            "Avoid ferries" to p.avoidFerries,"Avoid dirt / unpaved roads" to p.avoidDirt,
            "Prefer fewer U-turns among route choices" to p.fewerUTurns,"Prefer shortest distance" to p.shortest,
            "Lane arrows when map data is available" to p.laneGuidance)
        val boxes=options.map { (title,on)->CheckBox(a).apply {
            text=title;isChecked=on;textSize=20f;minHeight=(72*a.resources.displayMetrics.density).toInt();content.addView(this)
        } }
        content.addView(TextView(a).apply {text="Applies to new routes and rerouting. Avoidance depends on map data and provider support. Fewer U-turns ranks returned alternatives; some U-turns remain necessary. Neither provider offers a general avoid-all-bridges option. Review the route overview or add a different destination before riding. Lane arrows require provider coverage; no lane positions are guessed.";textSize=17f})
        AlertDialog.Builder(a).setTitle("Road preferences").setView(ScrollView(a).apply{addView(content)})
            .setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
                val keys=listOf("route_avoid_motorways","route_avoid_tolls","route_avoid_ferries","route_avoid_dirt","route_fewer_uturns","route_shortest","route_lanes")
                RidePreferences.prefs(a).edit().apply{keys.forEachIndexed{i,key->putBoolean(key,boxes[i].isChecked)}}.apply()
                saved?.invoke();Toast.makeText(a,"Road preferences saved for the next route",Toast.LENGTH_LONG).show()
            }.show()
    }
    fun ranked(routes:List<NavigationRoute>,p:RoadPreferences):List<NavigationRoute> {
        val choices=RouteChoices.ranked(routes)
        return when {
            p.fewerUTurns -> choices.sortedWith(compareBy<NavigationRoute>{(if(p.shortest)it.meters else it.millis.toDouble())+it.turns.count{t->kotlin.math.abs(t.sign)==8}*(if(p.shortest)750.0 else 90000.0)}.thenBy{it.meters})
            p.shortest -> choices.sortedBy{it.meters}
            else -> choices
        }
    }
}
