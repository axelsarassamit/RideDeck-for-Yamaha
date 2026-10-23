package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*

/** Saved pins stay local. Editing is a parked task; riding actions use the existing route preview. */
class FavoritePlacesActivity : RiderPage() {
    companion object {
        @JvmStatic fun open(activity: Activity) { activity.startActivity(Intent(activity, FavoritePlacesActivity::class.java)) }
        private fun dp(a: Activity, n: Int) = (n * a.resources.displayMetrics.density).toInt()
        private fun action(a: Activity, label: String, run: () -> Unit) = Button(a).apply {
            text=label; RideStyle.action(this,true); setOnClickListener { run() }
        }
        private fun sizeButtons(dialog: AlertDialog, a: Activity) {
            for (which in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL))
                dialog.getButton(which)?.apply { RideStyle.action(this,false) }
        }
        private fun chooseCategory(a: Activity, selected: (String) -> Unit) {
            val rows=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
            val dialog=AlertDialog.Builder(a).setTitle("Choose category")
                .setView(ScrollView(a).apply { addView(rows) }).setNegativeButton("Cancel",null).create()
            for (name in BikePlaces.categories(a)) rows.addView(action(a,name) { dialog.dismiss(); selected(name) })
            rows.addView(action(a,"Create my own category") {
                dialog.dismiss()
                val field=EditText(a).apply { hint="Category name"; textSize=22f; setSingleLine() }
                val add=AlertDialog.Builder(a).setTitle("New category").setView(field)
                    .setNegativeButton("Cancel",null).setPositiveButton("Create",null).create()
                add.setOnShowListener {
                    sizeButtons(add,a)
                    add.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        runCatching { BikePlaces.addCategory(a,field.text.toString()) }
                            .onSuccess { add.dismiss(); selected(it) }.onFailure { field.error=it.message }
                    }
                }; add.show()
            })
            dialog.setOnShowListener { sizeButtons(dialog,a) }; dialog.show()
        }
        fun savePlace(a: Activity, defaultName: String, destination: String, done: () -> Unit = {}) {
            edit(a,null,defaultName,destination,"Unsorted",done)
        }
        private fun edit(a: Activity, id: String?, oldName: String, destination: String, oldCategory: String, done: () -> Unit) {
            var category=oldCategory
            val fields=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(a,20),dp(a,8),dp(a,20),0) }
            val name=EditText(a).apply { hint="Place name or your own name"; setText(oldName); textSize=22f; minHeight=dp(a,72) }
            fields.addView(name)
            lateinit var categoryButton: Button
            categoryButton=action(a,"Category: $category") { chooseCategory(a) { category=it; categoryButton.text="Category: $it" } }
            fields.addView(categoryButton)
            fields.addView(TextView(a).apply { text=destination; textSize=17f; setPadding(0,dp(a,12),0,dp(a,12)) })
            val dialog=AlertDialog.Builder(a).setTitle(if(id==null) "Save favorite" else "Edit favorite")
                .setView(ScrollView(a).apply { addView(fields) }).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
            dialog.setOnShowListener {
                sizeButtons(dialog,a)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    runCatching {
                        if(id==null) BikePlaces.add(a,name.text.toString(),destination,category)
                        else BikePlaces.update(a,id,name.text.toString(),category)
                    }.onSuccess { dialog.dismiss(); Toast.makeText(a,"Favorite saved",Toast.LENGTH_SHORT).show(); done() }
                        .onFailure { name.error=it.message ?: "Could not save favorite" }
                }
            }; dialog.show()
        }
    }
    override fun onCreate(saved: Bundle?) { RideAppearance.prepare(this); super.onCreate(saved); build("Favorite places") }
    override fun onResume() { super.onResume(); show() }
    private fun show() {
        rows.removeAllViews()
        val places=BikePlaces.favorites(this)
        state.text="${places.size} / 20 saved. Use the place name or your own name. Organize while parked."
        row(button("Find and save a place") { startActivity(Intent(this,DestinationSearchActivity::class.java)) })
        row(button("Add coordinates") { addPin() })
        if(places.isEmpty()) row(text("No favorites yet. Search for a place or add its map coordinates."))
        for(category in BikePlaces.categories(this)) {
            val group=places.filter { it.category==category }
            if(group.isEmpty()) continue
            row(text(category,24f))
            for(place in group) row(button(place.name) { detail(place) })
        }
    }
    private fun detail(place: BikePlace) {
        rows.removeAllViews(); state.text=place.name+"\n"+place.category
        row(text(place.destination))
        row(button("Choose route") {
            val pin=runCatching { NavigationApi.coordinate(place.destination) }.getOrNull()
            if(pin==null) { state.text="This older favorite needs a map pin. Find and save the place again." }
            else if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),95)
                state.text="Allow precise location, then choose the route again."
            } else { NativeNavigation.start(this); RoutePreviewActivity.open(this,pin.copy(label=place.name)) }
        })
        row(button("Rename or change category") { edit(this,place.id,place.name,place.destination,place.category) { show() } })
        row(button("Delete favorite") {
            val confirm=AlertDialog.Builder(this).setTitle("Delete ${place.name}?").setMessage("Only this saved favorite will be removed.")
                .setNegativeButton("Keep",null).setPositiveButton("Delete") { _,_ -> BikePlaces.removeById(this,place.id); show() }.show()
            sizeButtons(confirm,this)
        })
        row(button("All favorites") { show() })
    }
    private fun addPin() {
        val field=EditText(this).apply { hint="Latitude, longitude"; textSize=22f; minHeight=dp(72); setSingleLine() }
        val dialog=AlertDialog.Builder(this).setTitle("Add map coordinates").setView(field)
            .setNegativeButton("Cancel",null).setPositiveButton("Next",null).create()
        dialog.setOnShowListener {
            sizeButtons(dialog,this)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=field.text.toString().trim()
                if(runCatching { NavigationApi.coordinate(value) }.getOrNull()==null) field.error="Enter valid latitude, longitude"
                else { dialog.dismiss(); savePlace(this,"",value) { show() } }
            }
        }; dialog.show()
    }
}
