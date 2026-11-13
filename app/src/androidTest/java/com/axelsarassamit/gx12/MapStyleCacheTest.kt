package com.axelsarassamit.gx12
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class MapStyleCacheTest {
    @Test fun encryptedStyleSurvivesReloadButCannotCrossAccountOrExpiry() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview"))
        val prefs=c.getSharedPreferences("map_style_cache",0)
        val prior=prefs.all.mapValues{it.value as String}
        try {
            val style="{\"version\":8,\"sources\":{},\"layers\":[],\"synthetic\":\"not-a-real-provider-key\"}"
            MapStyleCache.save(c,false,"synthetic-account-a",style,1000,61000)
            assertEquals(style,MapStyleCache.read(c,false,"synthetic-account-a",2000))
            assertNull(MapStyleCache.read(c,false,"synthetic-account-b",2000))
            assertNull(MapStyleCache.read(c,false,"synthetic-account-a",61000))
            assertFalse(prefs.all.values.joinToString().contains("not-a-real-provider-key"))
            prefs.edit().putString("day","corrupt").commit()
            assertNull(MapStyleCache.read(c,false,"synthetic-account-a",2000))
        } finally { prefs.edit().clear().also{e->prior.forEach{(k,v)->e.putString(k,v)}}.commit() }
    }
}
