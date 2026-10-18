package com.axelsarassamit.gx12

import android.view.View
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class MapPanelSettingsTest {
    @Test fun settingsAreInlineAndKeepValuesWithoutNestedMenus() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=RidePreferences.prefs(instrumentation.targetContext)
        val hadVoice=prefs.contains("navigation_voice");val voice=prefs.getBoolean("navigation_voice",true)
        val hadPanel=prefs.contains("phone_panel");val panel=prefs.getString("phone_panel","map")
        try {
            ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    SharedSetupTest.click(a,"Yamaha");SharedSetupTest.click(a,"Bike display & maps")
                    val all=SharedSetupTest.views(a.window.decorView)
                    assertFalse(SharedSetupTest.has(a,"MAP + PANEL SETTINGS"))
                    assertTrue(SharedSetupTest.has(a,"Panels"));assertTrue(SharedSetupTest.has(a,"Guidance"))
                    assertTrue(SharedSetupTest.has(a,"Routing & traffic"))
                    val choice=all.filterIsInstance<Switch>().single{it.tag=="navigation_voice"}
                    choice.performClick();assertEquals(!voice,prefs.getBoolean("navigation_voice",voice))
                    val spinner=all.filterIsInstance<Spinner>().single{it.tag=="phone_panel"};spinner.setSelection(2)
                    val account=all.filterIsInstance<EditText>().single{it.hint.toString()=="New MapTiler key"}
                    assertFalse(account.isShown);assertFalse(account.isSaveEnabled)
                    SharedSetupTest.click(a,"Map & routing accounts · Show");assertTrue(account.isShown)
                    SharedSetupTest.click(a,"Map & routing accounts · Hide");assertFalse(account.isShown)
                }
                instrumentation.waitForIdleSync()
                assertEquals("music",prefs.getString("phone_panel","map"))
                scenario.recreate();instrumentation.waitForIdleSync()
                scenario.onActivity { a ->
                    val all=SharedSetupTest.views(a.window.decorView)
                    assertEquals(2,all.filterIsInstance<Spinner>().single{it.tag=="phone_panel"}.selectedItemPosition)
                    assertEquals(!voice,all.filterIsInstance<Switch>().single{it.tag=="navigation_voice"}.isChecked)
                    SharedSetupTest.shot(a,"flat-map-panel")
                    SharedSetupTest.click(a,"Back");assertTrue(SharedSetupTest.has(a,"Bike display & maps"))
                }
            }
            ActivityScenario.launch(MapPanelSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { a ->assertTrue(SharedSetupTest.has(a,"Phone panel"));assertFalse(SharedSetupTest.has(a,"Navigation and panels")) }
            }
        } finally {
            prefs.edit().apply { if(hadVoice)putBoolean("navigation_voice",voice) else remove("navigation_voice");if(hadPanel)putString("phone_panel",panel) else remove("phone_panel") }.commit()
        }
    }
}
