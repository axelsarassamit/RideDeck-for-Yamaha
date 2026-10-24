package com.axelsarassamit.gx12

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Own-app menu smoke test. Never starts a ride, connects to a bike or inserts production telemetry. */
class ReleaseMenuTest {
    @Test fun parkedMenusOpenAndLargeControlsRespond() {
        importProviderSetup()
        for (type in listOf(BikeDataActivity::class.java, DestinationSearchActivity::class.java,
            RideBookActivity::class.java, FuelStationsActivity::class.java, TrafficActivity::class.java)) {
            ActivityScenario.launch(type).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                val controls = views(activity.window.decorView).filterIsInstance<Button>()
                assertTrue("No controls on ${type.simpleName}", controls.isNotEmpty())
                val density = activity.resources.displayMetrics.density
                controls.filter { it.visibility == View.VISIBLE }.forEach {
                    assertTrue("Small control: ${it.text}, height=${it.height}, density=$density", it.height >= 56 * density)
                }
                assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
                if (activity is BikeDataActivity) {
                    for (label in listOf("Connect to parked Yamaha", "Read bike snapshot", "Stop bike data"))
                        assertTrue(controls.any { it.text.toString() == label })
                    controls.single { it.text.toString() == "Data refresh rate" }.performClick()
                }
                if (activity is DestinationSearchActivity)
                    assertTrue(views(activity.window.decorView).filterIsInstance<android.widget.EditText>().any { it.hint.toString().contains("store") })
                val root=activity.window.decorView
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                File(activity.getExternalFilesDir("tests"),type.simpleName+".png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
            } }
        }
        // End on the page needed for the parked-bike test; no connection is attempted here.
    }
    companion object {
        internal fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
            (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
        internal fun importProviderSetup() {
            val c = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(c.getExternalFilesDir("tests"), "providers.json")
            if (!file.exists()) return
            try {
                require(file.length() < 4096)
                val json = JSONObject(file.readText(Charsets.UTF_8))
                for (name in listOf("maptiler", "tomtom")) {
                    val value = json.optString(name)
                    if (value.isNotBlank()) NavigationSecrets.save(c, name, value)
                }
            } finally { check(file.delete()) }
        }
    }
}
