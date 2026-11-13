package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test

class MapCachePolicyTest {
    @Test fun cacheDirectivesBoundStyleReuseAndNeverRetainDeniedResources() {
        assertEquals(0L,MapCachePolicy.lifetimeMillis("no-store, max-age=3600"))
        assertEquals(0L,MapCachePolicy.lifetimeMillis("no-cache"))
        assertEquals(60_000L,MapCachePolicy.lifetimeMillis("public, max-age=60"))
        assertEquals(86_400_000L,MapCachePolicy.lifetimeMillis("max-age=999999999"))
        // The real style endpoint currently omits Cache-Control. Use a bounded personal cache.
        assertEquals(3_600_000L,MapCachePolicy.lifetimeMillis(null))
    }
    @Test fun cachedStyleRejectsClockReversalExpiredOrDifferentAccount() {
        assertTrue(MapCachePolicy.reusable("a","a",1000,61000,2000))
        assertFalse(MapCachePolicy.reusable("a","b",1000,61000,2000))
        assertFalse(MapCachePolicy.reusable("a","a",1000,61000,999))
        assertFalse(MapCachePolicy.reusable("a","a",1000,61000,61000))
        assertEquals(64L*1024*1024,MapCachePolicy.diskBudget(300L*1024*1024))
        assertEquals(256L*1024*1024,MapCachePolicy.diskBudget(3L*1024*1024*1024))
    }
}
