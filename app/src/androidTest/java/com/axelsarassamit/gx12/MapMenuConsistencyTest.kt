package com.axelsarassamit.gx12

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Regression: the flat tools menu must expose settings and readable action rows. */
class MapMenuConsistencyTest {
    @Test fun toolsMenuExposesSettingsAndNavigationActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(RideToolsActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val buttons = SharedSetupTest.views(activity.window.decorView).filterIsInstance<Button>()
                assertTrue(buttons.any { it.text == "Map settings" })
                assertTrue(buttons.any { it.text == "Stop navigation" })
                assertTrue(buttons.any { it.text == "Saved places" })
                val back = buttons.single { it.text == "Back" }
                assertTrue(back.height >= (48 * activity.resources.displayMetrics.density).toInt())
                assertTrue(buttons.single { it.text == "Map settings" }.hasOnClickListeners())
                SharedSetupTest.shot(activity,"consistent-map-tools")
            }
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun standaloneMapSettingsUseReadableUnclippedRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MapPanelSettingsActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val buttons = SharedSetupTest.views(activity.window.decorView).filterIsInstance<Button>()
                for (label in listOf("Preview bike map", "Test English voice", "Review location permission", "Back")) {
                    val button = buttons.single { it.text == label }
                    assertEquals(18f * activity.resources.displayMetrics.scaledDensity, button.textSize, 0.5f)
                    assertNull(button.transformationMethod)
                    assertTrue(button.minimumHeight >= (56 * activity.resources.displayMetrics.density).toInt())
                }
                SharedSetupTest.shot(activity,"consistent-map-settings")
            }
        }
    }
}
