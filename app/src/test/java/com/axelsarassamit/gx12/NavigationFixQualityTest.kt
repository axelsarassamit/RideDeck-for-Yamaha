package com.axelsarassamit.gx12
import org.junit.Assert.*
import org.junit.Test

class NavigationFixQualityTest {
    private fun fix(seconds:Long=10,lat:Double=13.7,accuracy:Double=5.0,gps:Boolean=true)=NavigationFix(lat,100.5,accuracy,seconds*1_000_000_000,gps)
    @Test fun staleFutureAndOutOfOrderNeverReplaceLiveFix() {
        assertFalse(NavigationFixQuality.accept(fix(1),null,12_000_000_000))
        assertFalse(NavigationFixQuality.accept(fix(13),null,12_000_000_000))
        assertFalse(NavigationFixQuality.accept(fix(9),fix(10),12_000_000_000))
        assertFalse(NavigationFixQuality.accept(fix(10),fix(10),12_000_000_000))
    }
    @Test fun wifiDoesNotDisplaceFreshAccurateGpsButCanRecoverAfterLoss() {
        assertFalse(NavigationFixQuality.accept(fix(11,accuracy=35.0,gps=false),fix(),11_000_000_000))
        assertTrue(NavigationFixQuality.accept(fix(16,accuracy=20.0,gps=false),fix(),16_000_000_000))
        assertTrue(NavigationFixQuality.accept(fix(11),fix(gps=false),11_000_000_000))
    }
    @Test fun impossibleJumpIsRejectedWithoutBlockingNormalMovementOrReacquisition() {
        assertFalse(NavigationFixQuality.accept(fix(11,lat=13.71),fix(),11_000_000_000))
        assertTrue(NavigationFixQuality.accept(fix(11,lat=13.7003),fix(),11_000_000_000))
        assertTrue(NavigationFixQuality.accept(fix(25,lat=13.71),fix(),25_000_000_000))
    }
    @Test fun invalidCoordinatesAndUncertainSpeedAreNotPresentedAsReliable() {
        assertFalse(NavigationFixQuality.accept(fix(lat=Double.NaN),null,10_000_000_000))
        assertFalse(NavigationFixQuality.accept(fix(accuracy=51.0),null,10_000_000_000))
        assertNull(NavigationFixQuality.speed(100.0,0.3))
        assertNull(NavigationFixQuality.speed(20.0,6.0))
        assertNull(NavigationFixQuality.speed(null,0.3))
        assertEquals(20.0,NavigationFixQuality.speed(20.0,0.3)!!,0.0)
        assertNull(NavigationFixQuality.heading(0.4,270.0,2.0))
        assertNull(NavigationFixQuality.heading(20.0,270.0,80.0))
        assertEquals(270.0,NavigationFixQuality.heading(20.0,270.0,4.0)!!,0.0)
    }
}
