package com.axelsarassamit.gx12

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.pillion.core.CcuAdditionalFields
import app.pillion.core.CcuProtocol
import app.pillion.protocol.YamahaPhoneStatus
import app.pillion.protocol.YamahaStatusUpdates
import org.junit.Assert.*
import org.junit.Test

/** Runs in the isolated preview package, without connecting or changing a bike. */
class BikeDataDiagnosticsTest {
    private fun views(v: View): List<View> = listOf(v) +
        if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun unknownSupportedPayloadIsExcludedButReplyMetadataAndWrittenStatusAreExported() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        BikeDataStore.start(c); BikeDataStore.status("Preview-only diagnostics test", true)
        try {
            val secretFixture = "PRIVATE-UNKNOWN-FIELD-DO-NOT-EXPORT".toByteArray()
            val item = CcuProtocol.integer(1,3) + byteArrayOf(0x12,secretFixture.size.toByte()) + secretFixture
            BikeDataStore.accept(c, CcuProtocol.Message(0x10f,
                CcuProtocol.integer(3,1248) + byteArrayOf(0x22,item.size.toByte()) + item))
            BikeDataStore.additional(c,listOf(CcuAdditionalFields.Observation(3,CcuAdditionalFields.Result.REPLIED,secretFixture.size)))
            BikeDataStore.phoneStatusWritten(c,YamahaPhoneStatus(80,false),YamahaStatusUpdates.Kind.BATTERY)
            assertFalse(BikeDataStore.state.raw.containsKey("Vehicle ID 3"))
            val file = BikeDataStore.export(c)
            val exported = file.readText()
            assertFalse(exported.contains(secretFixture.joinToString("") { "%02x".format(it.toInt() and 255) }))
            assertFalse(exported.contains("PRIVATE-UNKNOWN-FIELD"))
            assertTrue(exported.contains("additional_fields"))
            assertTrue(exported.contains("phone_status_written"))
            assertTrue(exported.contains("\"charging\":false"))
            assertTrue(BikeDataStatusText.format(BikeDataStore.state,android.os.SystemClock.elapsedRealtime()).contains("meaning not verified"))
            file.delete()
        } finally { BikeDataStore.stop(c,"Preview test finished") }
    }

    @Test fun newControlsRemainLargeAndAutoConnectPreferenceSurvivesRecreation() {
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
                    for (label in listOf("Check additional bike fields","Save private test log")) {
                        val button=all.filterIsInstance<Button>().single { it.text.toString().startsWith(label) }
                        assertTrue(button.minHeight >= (72*a.resources.displayMetrics.density).toInt())
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

    @Test fun optionalMusicTimeoutKeepsAuthenticationAndBlocksUncertainBulkState() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val reports=mutableListOf<String>();var ended=false
            val client=CcuDiagnosticClient(instrumentation.targetContext,{ reports.add(it) },{}, { ended=true })
            fun field(name:String)=CcuDiagnosticClient::class.java.getDeclaredField(name).apply { isAccessible=true }
            try {
                field("authenticated").setBoolean(client,true)
                field("optionalMusicTransfer").setBoolean(client,true)
                field("stage").set(client,"active")
                field("transfer").set(client,byteArrayOf(1,0x16,0,0,0))
                (field("transferTimeout").get(client) as Runnable).run()
                assertFalse(ended)
                assertFalse(field("closed").getBoolean(client))
                assertTrue(field("authenticated").getBoolean(client))
                assertTrue(field("bulkBlocked").getBoolean(client))
                assertEquals("",field("stage").get(client))
                assertEquals(0,(field("transfer").get(client) as ByteArray).size)
                val begin=CcuDiagnosticClient::class.java.getDeclaredMethod("begin",ByteArray::class.java).apply { isAccessible=true }
                begin.invoke(client,byteArrayOf(1,0x16,0,0,0))
                assertEquals("",field("stage").get(client))
                assertTrue(reports.any { it.contains("Live bike data continues") })
                assertTrue(reports.last().contains("Reconnect"))
            } finally { client.close() }
        }
    }

}
