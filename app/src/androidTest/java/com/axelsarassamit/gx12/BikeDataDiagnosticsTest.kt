package com.axelsarassamit.gx12

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.pillion.protocol.YamahaPhoneStatus
import app.pillion.protocol.YamahaStatusUpdates
import org.junit.Assert.*
import org.junit.Test

/** Runs in the isolated preview package, without connecting or changing a bike. */
class BikeDataDiagnosticsTest {
    private fun views(v: View): List<View> = listOf(v) +
        if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun testControlsAreAbsentAndAutoConnectPreferenceSurvivesRecreation() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = RidePreferences.prefs(c)
        val existed = prefs.contains("bike_data_auto")
        val old = prefs.getBoolean("bike_data_auto",true)
        try {
            ActivityScenario.launch(BikeDataActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    val all=views(a.window.decorView)
                    val auto=all.filterIsInstance<CheckBox>().first { it.text.toString().startsWith("Auto-connect") }
                    auto.isChecked=false
                    assertFalse(prefs.getBoolean("bike_data_auto",true))
                    val labels = all.filterIsInstance<Button>().map { it.text.toString() }
                    for (testLabel in listOf("Check additional bike fields", "Save private test log", "Yamaha test panel")) {
                        assertFalse(labels.any { it.startsWith(testLabel) })
                    }
                }
                scenario.recreate()
                scenario.onActivity { a ->
                    assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().first { it.text.toString().startsWith("Auto-connect") }.isChecked)
                }
            }
        } finally {
            prefs.edit().apply { if(existed)putBoolean("bike_data_auto",old) else remove("bike_data_auto") }.commit()
        }
    }
    @Test fun combinedStatusExportAndMonitorUseRealPhoneReadings() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        val latch=java.util.concurrent.CountDownLatch(1)
        var snapshot: YamahaPhoneStatus?=null
        var monitor: YamahaPhoneStatusMonitor?=null
        instrumentation.runOnMainSync {
            monitor=YamahaPhoneStatusMonitor(c,"") { value,_ -> snapshot=value;latch.countDown() }.also { it.start() }
        }
        try {
            assertTrue(latch.await(12,java.util.concurrent.TimeUnit.SECONDS))
            val value=snapshot!!
            assertTrue(value.batteryPercent in 0..100)
            assertNotNull(value.charging)
            assertTrue(value.phoneVolume in 0..100)
            assertTrue(value.mediaVolume in 0..100)
            if(android.os.Build.VERSION.SDK_INT>=29) assertTrue(value.thermal in 0..2)
            BikeDataStore.start(c);BikeDataStore.status("Preview-only combined status",true)
            BikeDataStore.phoneStatusWritten(c,value,YamahaStatusUpdates.Kind.COMBINED)
            val text=BikeDataStatusText.phoneWrites(BikeDataStore.state,android.os.SystemClock.elapsedRealtime())
            assertTrue(text.contains("Combined phone status: ${value.batteryPercent}%"))
            assertEquals(value.charging,BikeDataStore.state.phoneWrites[YamahaStatusUpdates.Kind.COMBINED]?.charging)
        } finally {
            instrumentation.runOnMainSync { monitor?.close() }
            BikeDataStore.stop(c,"Preview test finished")
        }
    }

}
