package com.axelsarassamit.gx12
import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TrafficActivity:RiderPage() {
    companion object { @JvmStatic fun open(a:Activity) { a.startActivity(Intent(a,TrafficActivity::class.java)) } }
    private var lastRequest=0L
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved);build("Traffic nearby")
        state.text="TomTom reports near your current position. Coverage may be incomplete. This is a nearby list, not a claim that every incident affects your route."
        row(button("Check traffic") { load() })
        row(text(NativeNavigation.trafficAlertSummary()))
        row(text("TomTom motorcycle routing uses current traffic when enabled. During navigation, route refresh every 5 minutes can respond to new closures and delays. Turn it off in Map and routing accounts to reduce requests."))
    }
    private fun load() {
        if(!TomTomApi.configured(this)) { state.text="Add your TomTom key in Map and routing accounts first.";return }
        if(android.os.SystemClock.elapsedRealtime()-lastRequest<60000 && lastRequest!=0L) { state.text="Wait one minute before checking again.";return }
        val fix=freshLocation() ?: run { NativeNavigation.start(this);state.text="A recent GPS fix is needed. Open the map, allow precise location, then try again.";return }
        lastRequest=android.os.SystemClock.elapsedRealtime();state.text="Checking current traffic…"
        worker.execute {
            val result=runCatching { TomTomApi.incidents(applicationContext,fix) }
            runOnUiThread { if(isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { reports->rows.removeAllViews();row(button("Check traffic") { load() });reports.forEach { row(text(it)) }
                    state.text=if(reports.isEmpty()) "No nearby reports returned. This does not guarantee a clear road." else "${reports.size} nearby reports · checked ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date())} · TomTom"
                }.onFailure { state.text="Traffic unavailable. Your existing route is still available; check internet and API access." }
            }
        }
    }
}
