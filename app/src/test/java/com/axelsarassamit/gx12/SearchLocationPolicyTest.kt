package com.axelsarassamit.gx12

import org.junit.Assert.*
import org.junit.Test

class SearchLocationPolicyTest {
    private val now=120_000_000_000L
    private fun fix(age:Long=1_000_000_000L,accuracy:Double=20.0)=NavigationFix(13.7,100.5,accuracy,now-age,true)
    @Test fun rejectsExpiredFutureInvalidAndInaccuratePositions() {
        assertTrue(SearchLocationPolicy.valid(fix(),now))
        assertTrue(SearchLocationPolicy.valid(fix(60_000_000_000L,1000.0),now))
        for(f in listOf(fix(60_000_000_001L),fix(-1),fix(accuracy=1000.1),fix(accuracy=Double.NaN),fix().copy(latitude=91.0)))
            assertFalse(SearchLocationPolicy.valid(f,now))
    }
    @Test fun prefersCurrentNavigationThenBestValidRecentLastKnown() {
        val navigation=fix(accuracy=50.0);val precise=fix(accuracy=10.0)
        assertEquals(navigation,SearchLocationPolicy.choose(navigation,listOf(precise),now))
        assertEquals(precise,SearchLocationPolicy.choose(fix(61_000_000_000L),listOf(fix(accuracy=300.0),precise),now))
        assertNull(SearchLocationPolicy.choose(null,listOf(fix(-1)),now))
    }
    @Test fun approximatePermissionCannotReuseAnOldPreciseNavigationFix() {
        val precise=fix(accuracy=10.0);val coarse=fix(accuracy=1000.0).copy(satellite=false)
        assertEquals(coarse,SearchLocationPolicy.choose(precise,listOf(coarse),now,false))
        assertNull(SearchLocationPolicy.choose(precise,emptyList(),now,false))
    }
    @Test fun aSlowProviderCannotExtendTheAnchorLifetimeForFallback() {
        val anchor=fix(59_000_000_000L)
        assertEquals(anchor,SearchLocationPolicy.choose(anchor,emptyList(),now))
        assertNull(SearchLocationPolicy.choose(anchor,emptyList(),now+2_000_000_000L))
    }
}
