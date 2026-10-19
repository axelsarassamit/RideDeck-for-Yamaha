package app.pillion.core
import org.junit.Assert.*
import org.junit.Test
class FunRidePlanTest {
    @Test fun seedsRemainValidAndDirectionChanges() {
        val start=FunPoint(13.5,100.5)
        val north=FunRidePlan.seeds(start,60,0.0);val south=FunRidePlan.seeds(start,60,180.0)
        assertTrue(north.all{it.latitude>start.latitude});assertTrue(south.all{it.latitude<start.latitude})
        assertTrue(north[0].longitude<start.longitude);assertTrue(north[1].longitude>start.longitude)
    }
    @Test fun adaptiveSearchChangesSeedReachAndRejectsInvalidScale() {
        val start=FunPoint(13.5,100.5)
        val near=FunRidePlan.seeds(start,240,0.0,.5).first()
        val far=FunRidePlan.seeds(start,240,0.0,2.0).first()
        assertTrue(far.latitude>near.latitude)
        assertTrue(runCatching{FunRidePlan.seeds(start,240,0.0,Double.NaN)}.isFailure)
    }
    @Test fun geodesicCrossesDatelineSafely() { val p=FunRidePlan.offset(FunPoint(10.0,179.99),20.0,90.0);assertTrue(p.longitude in -180.0..180.0);assertTrue(p.longitude<0) }
    @Test fun durationFitAndUTurnPenalty() { assertTrue(FunRidePlan.score(60.0,60,0)<FunRidePlan.score(80.0,60,0));assertTrue(FunRidePlan.score(60.0,60,0)<FunRidePlan.score(60.0,60,2)) }
    @Test(expected=IllegalArgumentException::class) fun unsupportedDurationRejected() { FunRidePlan.seeds(FunPoint(0.0,0.0),1,0.0) }
    @Test(expected=IllegalArgumentException::class) fun invalidCoordinatesRejected() { FunPoint(Double.NaN,0.0) }
}
