package com.axelsarassamit.gx12

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Own-app checklist only: never connects a bike or sends a message. Restores user results. */
class YamahaTestPanelTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun tap(a:YamahaTestPanelActivity,label:String) {
        val button=views(a.window.decorView).filterIsInstance<Button>().first { it.text.toString().startsWith(label) }
        assertTrue(button.minHeight >= (56*a.resources.displayMetrics.density).toInt());button.performClick()
    }
    @Test fun readsRealPhoneBatteryWithoutBikeConnection() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ready=java.util.concurrent.CountDownLatch(1)
        var monitor:YamahaPhoneStatusMonitor?=null
        var observed:app.pillion.protocol.YamahaPhoneStatus?=null
        instrumentation.runOnMainSync {
            monitor=YamahaPhoneStatusMonitor(instrumentation.targetContext, "") { status,_->observed=status;ready.countDown() }.also { it.start() }
        }
        try {
            assertTrue("No phone-status callback",ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            assertNotNull(observed?.batteryPercent)
            assertTrue(observed!!.batteryPercent!! in 0..100)
            assertNotNull(observed!!.charging)
        } finally { instrumentation.runOnMainSync { monitor?.close() } }
    }
    @Test fun observationsSurviveRotationAndStayBuildScopedInExport() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=c.getSharedPreferences("yamaha_test_results",0)
        val version="${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val prefix="$version/connection"
        val keys=listOf("$prefix/result","$prefix/note","$prefix/at","older-build/connection/result")
        val backup=keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().remove("$prefix/result").remove("$prefix/note").remove("$prefix/at").putString("older-build/connection/result","Passed").commit()
            ActivityScenario.launch(YamahaTestPanelActivity::class.java).use { scenario->
                scenario.onActivity { a->tap(a,"Not tested · Five-minute");views(a.window.decorView).filterIsInstance<EditText>().single().setText("Checklist UI test only") }
                scenario.recreate()
                scenario.onActivity { a->
                    assertEquals("Checklist UI test only",views(a.window.decorView).filterIsInstance<EditText>().single().text.toString())
                    tap(a,"Problem ·")
                    assertEquals("Failed",prefs.getString("$prefix/result",null))
                    tap(a,"Failed · Five-minute")
                    tap(a,"Blocked / not supported")
                    assertEquals("Blocked",prefs.getString("$prefix/result",null))
                    val method=YamahaTestPanelActivity::class.java.getDeclaredMethod("report").apply { isAccessible=true }
                    val report=JSONObject(method.invoke(a) as String)
                    assertEquals(version,report.getString("currentBuild"))
                    val builds=report.getJSONArray("builds")
                    val current=(0 until builds.length()).map { builds.getJSONObject(it) }.first { it.getString("build")==version }
                    assertEquals("Blocked",current.getJSONArray("checks").getJSONObject(0).getString("result"))
                    assertEquals("Passed",prefs.getString("older-build/connection/result",null))
                    tap(a,"Not tested · Battery and crank")
                    assertFalse(views(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()=="Not available in this build" }.isEnabled)
                    tap(a,"Back to checks")

                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a->
                    val buttons=views(a.window.decorView).filterIsInstance<Button>()
                    assertTrue(buttons.first { it.text.toString().startsWith("Bike data and phone") }.height>0)
                    val root=a.window.decorView
                    val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    java.io.File(a.getExternalFilesDir("tests"),"yamaha-test-panel.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
            }
        } finally {
            prefs.edit().apply { keys.forEach { key->when(val value=backup[key]) { is String->putString(key,value);is Long->putLong(key,value);else->remove(key) } } }.commit()
        }
    }
}
