package com.axelsarassamit.gx12

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Persistence tests use only the isolated preview package; no network or bike connection. */
class BikeFavoritesTest {
    private fun fixture(run: (android.content.Context) -> Unit) {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview")) { "Favorites fixtures require the isolated preview package" }
        val prefs=RidePreferences.prefs(c)
        val keys=listOf("bike_favorites","bike_favorite_categories","bike_home","bike_home_label","bike_work","bike_work_label")
        val before=keys.associateWith { prefs.getString(it,null) }
        try { keys.forEach { prefs.edit().remove(it).commit() }; run(c) }
        finally { val edit=prefs.edit(); before.forEach { (key,value) -> if(value==null) edit.remove(key) else edit.putString(key,value) }; edit.commit() }
    }
    @Test fun legacyFavoritesKeepPinsStableIdsAndHomeWorkWhenEdited() = fixture { c ->
        val prefs=RidePreferences.prefs(c)
        val legacy="""[{"name":"บ้าน / Home","destination":"13.60001,100.59002"},{"name":"Same pin","destination":"13.60001,100.59002"}]"""
        prefs.edit().putString("bike_favorites",legacy).putString("bike_home","13.11,100.22")
            .putString("bike_home_label","My home").putString("bike_work","13.33,100.44").putString("bike_work_label","Office").commit()
        val old=BikePlaces.favorites(c)
        assertEquals(2,old.size); assertEquals("Unsorted",old[0].category)
        assertNotEquals(old[0].id,old[1].id)
        assertEquals(old.map { it.id },BikePlaces.favorites(c).map { it.id })
        assertEquals(legacy,prefs.getString("bike_favorites",null)) // Reading does not rewrite old records.
        BikePlaces.update(c,old[0].id,"My condo","Friends & family")
        val saved=BikePlaces.favorites(c)
        assertEquals("My condo",saved[0].name);assertEquals(old[0].destination,saved[0].destination)
        assertEquals(old[0].id,saved[0].id);assertEquals(old[1],saved[1])
        assertEquals("13.11,100.22",prefs.getString("bike_home",null));assertEquals("My home",prefs.getString("bike_home_label",null))
        assertEquals("13.33,100.44",prefs.getString("bike_work",null));assertEquals("Office",prefs.getString("bike_work_label",null))
    }
    @Test fun customCategoriesPersistAndDeletingOneDuplicatePinKeepsTheOther() = fixture { c ->
        assertEquals("Weekend rides",BikePlaces.addCategory(c,"  Weekend rides  "))
        assertEquals("Weekend rides",BikePlaces.addCategory(c,"weekend RIDES"))
        BikePlaces.add(c,"Coffee stop","13.5,100.5","Weekend rides")
        BikePlaces.add(c,"Meeting point","13.5,100.5","Food & drink")
        val old=BikePlaces.favorites(c)
        BikePlaces.update(c,old[1].id,"Morning coffee","Weekend rides")
        BikePlaces.removeById(c,old[0].id)
        val remaining=BikePlaces.favorites(c).single()
        assertEquals(old[1].id,remaining.id);assertEquals("Morning coffee",remaining.name)
        assertEquals("13.5,100.5",remaining.destination);assertEquals("Weekend rides",remaining.category)
        assertEquals(1,BikePlaces.categories(c).count { it=="Weekend rides" })
        BikePlaces.removeById(c,remaining.id)
        assertTrue(BikePlaces.categories(c).contains("Weekend rides"))
    }
    @Test fun fullLegacyListCannotBeOverwrittenByFailedAdd() = fixture { c ->
        val array=JSONArray()
        repeat(20) { array.put(JSONObject().put("name","Place $it").put("destination","13.$it,100.5")) }
        RidePreferences.prefs(c).edit().putString("bike_favorites",array.toString()).commit()
        val before=BikePlaces.favorites(c)
        try { BikePlaces.add(c,"Extra","13.5,100.5","Unsorted");fail("Full list accepted another favorite") } catch(_:IllegalArgumentException) {}
        assertEquals(before,BikePlaces.favorites(c))
    }
}
