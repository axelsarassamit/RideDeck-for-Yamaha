package com.axelsarassamit.gx12;
import org.junit.Test;
import static org.junit.Assert.*;

public class CockpitClockCycleTest {
    @Test public void returnsTimeBetweenArrivalAndDistanceAndWraps() {
        assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(4999,true,true));
        assertEquals(CockpitClockCycle.Mode.ETA, CockpitClockCycle.mode(5000,true,true));
        assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(10000,true,true));
        assertEquals(CockpitClockCycle.Mode.DISTANCE, CockpitClockCycle.mode(15000,true,true));
        assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(20000,true,true));
    }
    @Test public void missingRouteOrArrivalNeverShowsInventedTripValues() {
        assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(5000,false,true));
        assertEquals(CockpitClockCycle.Mode.DISTANCE, CockpitClockCycle.mode(15000,false,true));
        assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(15000,true,false));
        for (long time=0; time<40000; time+=1000)
            assertEquals(CockpitClockCycle.Mode.TIME, CockpitClockCycle.mode(time,false,false));
    }
}
