package com.axelsarassamit.gx12
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import java.util.concurrent.Executors

/** Full-height place search with deliberate requests and exact, locally saved pins. */
class DestinationSearchActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private var generation=0
    private lateinit var field:EditText
    private lateinit var status:TextView
    private lateinit var results:LinearLayout
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun button(label:String,action:()->Unit)=Button(this).apply {
        text=label;RideStyle.action(this,true);setOnClickListener { action() }
    }
    override fun onCreate(saved:Bundle?) {
        RideAppearance.prepare(this); super.onCreate(saved);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(8));setBackgroundColor(RideStyle.BACKGROUND) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(16) + bars.left, dp(16) + bars.top, dp(16) + bars.right, dp(8) + bars.bottom)
            insets
        }
        page.addView(TextView(this).apply { text="Find a destination";textSize=28f;setTextColor(RideStyle.TEXT) })
        field=EditText(this).apply { hint="Street, store, address or coordinates";textSize=22f;setSingleLine();setText(intent.getStringExtra("query").orEmpty());
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;setOnEditorActionListener { _,a,_ -> if(a==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search();true } else false } }
        page.addView(field,LinearLayout.LayoutParams(-1,dp(80)))
        val row=LinearLayout(this)
        row.addView(button("Search") { search() },LinearLayout.LayoutParams(0,dp(56),1f).apply { leftMargin=dp(4);rightMargin=dp(4) })
        row.addView(button("Paste map pin") {
            val clip=getSystemService(ClipboardManager::class.java).primaryClip
            val value=clip?.takeIf { it.itemCount>0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if(value.isBlank()) status.text="Copy coordinates or a Google Maps place link first."
            else if(value.contains("https://")) share(value)
            else { field.setText(value);search() }
        },LinearLayout.LayoutParams(0,dp(56),1f).apply { leftMargin=dp(4);rightMargin=dp(4) });page.addView(row)
        status=TextView(this).apply { text="Search by name, or paste latitude, longitude.";textSize=16f;setTextColor(RideStyle.MUTED);setPadding(0,dp(8),0,dp(8)) };page.addView(status)
        results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { addView(results) },LinearLayout.LayoutParams(-1,0,1f))
        page.addView(button("Manage favorites") { FavoritePlacesActivity.open(this) });
        page.addView(button("Back") { finish() });setContentView(page);page.requestApplyInsets();showSaved("")
    }
    private fun share(text:String) { startActivity(Intent(this,SharedLocationActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,text)) }
    private fun showSaved(query:String) {
        for(p in BikePlaces.favorites(this).filter { query.isBlank() || (it.name.contains(query,true) || it.category.contains(query,true)) })
            runCatching { NavigationApi.coordinate(p.destination) }.getOrNull()?.let { add(it.copy(label=p.name,kind="saved",provider=p.category)) }
        val prefs=RidePreferences.prefs(this)
        for((name,key) in listOf("Home" to "bike_home","Work" to "bike_work")) {
            val label=prefs.getString(key+"_label",name).orEmpty()
            if(query.isBlank() || label.contains(query,true) || name.contains(query,true))
                prefs.getString(key,"")?.let { runCatching { NavigationApi.coordinate(it) }.getOrNull()?.let { p -> add(p.copy(label=label,kind="saved")) } }
        }
    }
    private fun search() {
        val query=field.text.toString().trim();if(query.length<3) { status.text="Enter a name or latitude, longitude.";return }
        if(query.startsWith("https://")) { share(query);return }
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(field.windowToken,0)
        val id=++generation;results.removeAllViews();showSaved(query);status.text="Searching…"
        worker.execute {
            val answer=runCatching { NavigationApi.search(applicationContext,query,NativeNavigation.location) }
            runOnUiThread {
                if(id!=generation || isFinishing || isDestroyed) return@runOnUiThread
                answer.onSuccess { places -> status.text=if(places.isEmpty()) "No exact match. Paste a Google Maps pin." else "Check the destination pin before choosing a route.";places.forEach(::add) }
                    .onFailure { status.text="Search unavailable. Saved places and coordinates still work." }
            }
        }
    }
    private fun add(place:NavigationPlace) {
        val detail=(if(place.provider.isNotBlank()) place.provider+" · " else "")+when(place.kind) { "saved" -> "Saved place";"pin" -> "Exact coordinates";"poi" -> "Place / store";"address","system_address" -> "Address result";"road" -> "Street result";else -> "Area result: check pin" }
        results.addView(button(place.label+"\n"+detail) { choose(place) },LinearLayout.LayoutParams(-1,-2))
    }
    private fun choose(place:NavigationPlace) {
        AlertDialog.Builder(this).setTitle(place.label).setMessage("Check the map pin and route overview before starting.")
            .setNegativeButton("Cancel",null).setNeutralButton("Save place") { _,_ ->
                savePlace(place)
            }.setPositiveButton("Choose route") { _,_ ->
                if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),95)
                    Toast.makeText(this,"Allow precise location, then choose the destination again.",Toast.LENGTH_LONG).show()
                } else { NativeNavigation.start(this);RoutePreviewActivity.open(this,place) }
            }.show().also(::largeButtons)
    }
    private fun savePlace(place:NavigationPlace) {
        val pin="${place.latitude},${place.longitude}"
        val options=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val dialog=AlertDialog.Builder(this).setTitle("Save place").setView(options).setNegativeButton("Cancel",null).create()
        options.addView(button("Favorite · name and category") {
            dialog.dismiss()
            FavoritePlacesActivity.savePlace(this,place.label,pin) { results.removeAllViews();showSaved("") }
        })
        for((title,key) in listOf("Home" to "bike_home","Work" to "bike_work")) options.addView(button(title) {
            dialog.dismiss()
            val name=EditText(this).apply { setText(place.label);textSize=22f;minHeight=dp(72) }
            val labelDialog=AlertDialog.Builder(this).setTitle("Name for $title").setView(name).setNegativeButton("Cancel",null)
                .setPositiveButton("Save") { _,_ ->
                    val label=name.text.toString().trim().take(120).ifBlank { place.label }
                    RidePreferences.prefs(this).edit().putString(key,pin).putString(key+"_label",label).apply()
                    results.removeAllViews();showSaved("")
                    Toast.makeText(this,"$title saved",Toast.LENGTH_SHORT).show()
                }.show()
            largeButtons(labelDialog)
        })
        dialog.show();largeButtons(dialog)
    }
    private fun largeButtons(dialog:AlertDialog) {
        for(which in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE))
            dialog.getButton(which)?.apply { RideStyle.action(this,false) }
    }
    override fun onDestroy() { generation++;worker.shutdownNow();super.onDestroy() }
}
