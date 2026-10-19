package com.axelsarassamit.gx12

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*

/** Riding destinations are flat actions, separate from parked configuration. */
class RideToolsActivity:Activity() {
    companion object { @JvmStatic fun open(activity:Activity) { activity.startActivity(Intent(activity,RideToolsActivity::class.java)) } }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onCreate(saved:Bundle?) {
        RideAppearance.prepare(this);super.onCreate(saved)
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(RideStyle.BACKGROUND) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page){v,insets->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left+dp(12),bars.top+dp(4),bars.right+dp(12),bars.bottom+dp(4));insets
        }
        page.addView(TextView(this).apply {text="Ride tools";textSize=26f;setTextColor(RideStyle.TEXT);gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(-1,dp(44)))
        val actions=listOf<Pair<String,()->Unit>>(
            "Search" to { NavigationUi.search(this,null) },
            "Fun ride" to { startActivity(Intent(this,FunRideActivity::class.java)) },
            "Saved places" to { FavoritePlacesActivity.open(this) },
            "Home" to { savedPlace("Home","bike_home") },
            "Work" to { savedPlace("Work","bike_work") },
            "Fuel" to { FuelStationsActivity.open(this) },
            "Traffic" to { TrafficActivity.open(this) },
            "Ride book" to { RideBookActivity.open(this) },
            "Stop navigation" to { NativeNavigation.stopRoute();Toast.makeText(this,"Navigation stopped",Toast.LENGTH_SHORT).show() }
        )
        val columns=if(resources.configuration.screenWidthDp>=560)3 else 2
        val grid=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;tag="ride_tools_grid"}
        for(chunk in actions.chunked(columns)) {
            val row=LinearLayout(this)
            for((label,action) in chunk) row.addView(tile(label,action),LinearLayout.LayoutParams(0,dp(64),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})
            repeat(columns-chunk.size){row.addView(android.view.View(this),LinearLayout.LayoutParams(0,dp(64),1f))}
            grid.addView(row,LinearLayout.LayoutParams(-1,dp(70)))
        }
        // Normal phone sizes show the complete grid. Small multi-window keeps every tile reachable.
        page.addView(ScrollView(this).apply {isFillViewport=false;addView(grid)},LinearLayout.LayoutParams(-1,0,1f))
        page.addView(tile("Back"){finish()},LinearLayout.LayoutParams(-1,dp(56)))
        setContentView(page);androidx.core.view.ViewCompat.requestApplyInsets(page)
    }
    private fun tile(label:String,action:()->Unit)=Button(this).apply {
        text=label;contentDescription="Ride tools: $label";isAllCaps=false;textSize=20f;maxLines=2;minHeight=dp(56)
        gravity=Gravity.CENTER;setTextColor(RideStyle.TEXT);setPadding(dp(4),dp(2),dp(4),dp(2));background=RideStyle.row(this@RideToolsActivity);setOnClickListener{action()}
    }
    private fun savedPlace(label:String,key:String) {
        val place=NavigationApi.coordinate(RidePreferences.prefs(this).getString(key,"").orEmpty())?.copy(label=label)
        if(place==null){Toast.makeText(this,"Find your $label, then Save place > $label",Toast.LENGTH_LONG).show();NavigationUi.search(this,null);return}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),74)
            Toast.makeText(this,"Allow precise location, then tap $label again",Toast.LENGTH_LONG).show();return
        }
        RoutePreviewActivity.open(this,place)
    }
}
