package com.axelsarassamit.gx12

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Local manual observations, isolated by build. No automatic bike probes or pass marks. */
class YamahaTestPanelActivity:RiderPage() {
    companion object { @JvmStatic fun open(a:Activity) { a.startActivity(Intent(a,YamahaTestPanelActivity::class.java)) } }
    private val records by lazy { getSharedPreferences("yamaha_test_results",MODE_PRIVATE) }
    private val version get()="${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private var group:String?=null
    private var pendingOnly=false
    private var export:String?=null
    private var currentTest:String?=null
    private var draft:EditText?=null
    private var live:android.widget.TextView?=null
    private val updates=android.os.Handler(android.os.Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() { updateLive();updates.postDelayed(this,1000) } }
    private fun liveRow() { live=text("");row(live!!);updateLive() }
    private fun updateLive() {
        val s=BikeDataStore.state;val now=android.os.SystemClock.elapsedRealtime()
        live?.text="Bike data: ${s.status}\n"+
            "Authenticated: ${if(s.active && s.authenticated) "Yes" else "No"} · session ${if(s.connectedAt==0L) "not started" else if(!s.active) "ended" else "${(now-s.connectedAt)/1000} s"}\n"+
            "Keepalive: ${s.aliveRequests} requests / ${s.aliveReplies} replies written\n"+
            "Packets: ${s.packets} · last ${if(s.lastPacket==0L) "not received" else "${(now-s.lastPacket)/1000} s ago"}\n"+
            "Supported vehicle IDs: ${s.supportedIds.joinToString().ifEmpty { "not received" }}\nCounters are evidence only; checks are never passed automatically."
    }
    override fun onResume() { super.onResume();updates.post(tick) }
    override fun onPause() { updates.removeCallbacks(tick);super.onPause() }
    private fun key(id:String)="$version/$id"
    private fun result(id:String)=records.getString(key(id)+"/result","Not tested")!!
    override fun onCreate(saved:Bundle?) {
        RideAppearance.prepare(this);super.onCreate(saved);build("Yamaha test panel")
        group=saved?.getString("group");pendingOnly=saved?.getBoolean("pending")?:false
        val selected=YamahaTestCatalog.items.firstOrNull { it.id==saved?.getString("detail") }
        if(selected==null)render() else { detail(selected);draft?.setText(saved?.getString("draft")?:draft?.text) }
    }
    override fun onSaveInstanceState(out:Bundle) { out.putString("group",group);out.putBoolean("pending",pendingOnly);out.putString("detail",currentTest);out.putString("draft",draft?.text?.toString());super.onSaveInstanceState(out) }
    private fun render() {
        currentTest=null;draft=null
        rows.removeAllViews()
        val all=YamahaTestCatalog.items
        state.text="$version\n${all.count { result(it.id)=="Passed" }} / ${all.count { it.available }} available passed · ${all.count { !it.available }} future checks · ${all.count { result(it.id)=="Failed" }} problems\nParked use only. Results are your observations, saved for this build."
        liveRow()
        row(button("Bike data and phone status") { BikeDataActivity.open(this) })
        row(button("Group: ${group?:"All checks"}") {
            val groups=listOf("All checks")+all.map { it.group }.distinct()
            android.app.AlertDialog.Builder(this).setTitle("Choose checks").setItems(groups.toTypedArray()) { _,i->group=groups[i].takeUnless { i==0 };render() }.show()
        })
        row(button(if(pendingOnly) "Showing unfinished · show all" else "Show unfinished checks") { pendingOnly=!pendingOnly;render() })
        row(button("Next unfinished check") {
            val candidates=all.filter { it.available && (group==null || it.group==group) }
            val next=candidates.firstOrNull { result(it.id)=="Not tested" }?:candidates.firstOrNull { result(it.id)=="Failed" }
            if(next!=null) detail(next) else Toast.makeText(this,"No available checks waiting. Review blocked items in their group.",Toast.LENGTH_SHORT).show()
        })
        row(button("Save test report") {
            export=report()
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE,"yamaha-test-report-${BuildConfig.VERSION_NAME}-${System.currentTimeMillis()}.json"),97)
        })
        all.filter { (group==null || it.group==group) && (!pendingOnly || result(it.id)!="Passed") }.forEach { test->
            row(button("${result(test.id)} · ${test.title}\n${test.group} / ${test.stage}") { detail(test) })
        }
    }
    private fun detail(test:YamahaTest) {
        currentTest=test.id
        rows.removeAllViews();state.text="${test.title}\n${test.stage} · ${result(test.id)}"
        liveRow()
        row(text("Steps\n${test.steps}",21f));row(text("Pass when\n${test.expected}",21f))
        row(text("Notes stay on this phone unless you save a report. Avoid names, VIN, pairing details or message contents."))
        val note=EditText(this).apply { hint="What happened?";textSize=20f;setTextColor(RideStyle.TEXT);setHintTextColor(RideStyle.MUTED);minHeight=dp(96);setText(records.getString(key(test.id)+"/note","")) }
        draft=note;row(note)
        fun save(value:String) {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(note.windowToken,0)
            records.edit().putString(key(test.id)+"/result",value).putString(key(test.id)+"/note",note.text.toString().take(2000))
                .putLong(key(test.id)+"/at",System.currentTimeMillis()).apply();render()
        }
        row(button(if(test.available) "Pass · checked on this build" else "Not available in this build") { save("Passed") }.apply { isEnabled=test.available;alpha=if(test.available)1f else 0.5f })
        row(button("Problem · needs fixing") { save("Failed") })
        row(button("Blocked / not supported") { save("Blocked") })
        row(button("Save notes · test later") { save("Not tested") })
        row(button("Back to checks") { render() })
    }
    @Deprecated("Activity back compatibility") override fun onBackPressed() { if(currentTest!=null) render() else super.onBackPressed() }
    private fun report():String {
        val builds=records.all.keys.filter { it.endsWith("/result") }.map { it.substringBefore('/') }.toSet()+version
        return JSONObject().put("schema",1).put("app","RideDeck for Yamaha").put("currentBuild",version)
            .put("exportedAt",System.currentTimeMillis()).put("resultsAre","Manual observations; no automatic passes or bike credentials")
            .put("builds",JSONArray().apply { builds.sorted().forEach { build->put(JSONObject().put("build",build).put("checks",JSONArray().apply {
                YamahaTestCatalog.items.forEach { test->
                    val prefix="$build/${test.id}"
                    put(JSONObject().put("id",test.id).put("title",test.title).put("group",test.group).put("stage",test.stage)
                        .put("availableInCurrentBuild",if(build==version)test.available else JSONObject.NULL).put("result",records.getString("$prefix/result","Not tested")).put("note",records.getString("$prefix/note",""))
                        .put("observedAt",records.getLong("$prefix/at",0)))
                }
            })) } }).toString(2)
    }
    @Deprecated("Android activity result compatibility") override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data);if(request!=97)return
        val content=export;export=null
        val ok=result==RESULT_OK && content!=null && data?.data!=null && runCatching {
            contentResolver.openOutputStream(data.data!!)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }?:error("No destination")
        }.isSuccess
        Toast.makeText(this,if(ok) "Test report saved." else "Report not exported. Results remain saved on this phone.",Toast.LENGTH_LONG).show()
    }
}
