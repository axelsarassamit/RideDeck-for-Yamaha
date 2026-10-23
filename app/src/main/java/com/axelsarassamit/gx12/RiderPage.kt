package com.axelsarassamit.gx12
import android.app.Activity
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

/** Large parked pages share the cockpit colours, readable rows and system insets. */
abstract class RiderPage:Activity() {
    protected val worker=Executors.newSingleThreadExecutor()
    protected lateinit var page:LinearLayout
    protected lateinit var rows:LinearLayout
    protected lateinit var state:TextView
    protected fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    protected fun text(value:String,size:Float=18f)=TextView(this).apply {
        text=value;textSize=size;setTextColor(RideStyle.TEXT);setPadding(dp(12),dp(12),dp(12),dp(12))
    }
    protected fun button(label:String,action:()->Unit)=Button(this).apply {
        text=label;RideStyle.action(this,true);setOnClickListener { action() }
    }
    protected fun row(view:android.view.View)=rows.addView(view,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    protected fun build(title:String) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(RideStyle.BACKGROUND) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { v,insets->
            val edge=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(edge.left+dp(16),edge.top+dp(8),edge.right+dp(16),edge.bottom+dp(8));insets
        }
        page.addView(text(title,28f));state=text("");page.addView(state)
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { addView(rows) },LinearLayout.LayoutParams(-1,0,1f))
        page.addView(button("Back") { finish() });setContentView(page)
    }
    protected fun freshLocation():Location? {
        val found=mutableListOf<Location>();NativeNavigation.location?.let(found::add)
        runCatching { val manager=getSystemService(LocationManager::class.java);manager.getProviders(true).forEach { name->
            runCatching { manager.getLastKnownLocation(name) }.getOrNull()?.let(found::add)
        } }
        return found.filter { android.os.SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0..60_000_000_000L && it.hasAccuracy() && it.accuracy<=50 }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }
    override fun onDestroy() { worker.shutdownNow();super.onDestroy() }
}
