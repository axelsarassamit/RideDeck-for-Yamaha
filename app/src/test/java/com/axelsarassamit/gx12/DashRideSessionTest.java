package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashRideSessionTest {
    @Test public void idleDoesNotBecomeARideThroughElapsedTimeOrUiReads() {
        DashRideSessionState s=new DashRideSessionState();assertFalse(s.active());assertEquals(0,s.elapsed(10000));assertFalse(s.active());
    }
    @Test public void pauseStopsAccumulationAndResumeKeepsPriorTime() {
        DashRideSessionState s=new DashRideSessionState();s.start(1000);s.pause(4000);assertFalse(s.active());assertEquals(3000,s.elapsed(20000));
        s.start(22000);assertTrue(s.active());assertEquals(6000,s.elapsed(25000));
    }
    @Test public void duplicateStartAndProcessLossCannotCreateARestoredLease() {
        DashRideSessionState s=new DashRideSessionState();s.start(1000);s.start(2000);assertEquals(3000,s.elapsed(4000));
        DashRideSessionState restarted=new DashRideSessionState();assertFalse(restarted.active());assertEquals(0,restarted.elapsed(5000));
    }
    @Test public void endClearsPausedOrRunningRide() {
        DashRideSessionState s=new DashRideSessionState();s.start(0);s.end();assertFalse(s.active());assertEquals(0,s.elapsed(10000));
        s.start(11000);s.pause(12000);s.end();assertEquals(0,s.elapsed(15000));
    }
    @Test public void bridgeOnlyEnablesBackgroundForValidAcceptedActiveReply() {
        assertEquals("Dash background enabled",DashBridgeReply.renewal(1,true,true));
        assertEquals("Another RideDeck session is using Dash",DashBridgeReply.renewal(1,false,true));
        assertEquals("Dash needs a compatible update",DashBridgeReply.renewal(2,true,true));
        assertEquals("Dash needs a compatible update",DashBridgeReply.renewal(1,null,true));
        assertEquals("Dash needs a compatible update",DashBridgeReply.renewal(1,true,false));
    }
}
