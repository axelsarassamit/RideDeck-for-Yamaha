package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashStartTokenGateTest {
    @Test public void visibleTrustedExplicitRequestSendsOnlyOnce() {
        DashStartTokenGate gate = new DashStartTokenGate(1000);
        assertTrue(gate.consume(1100,true,true,true,true,true));
        assertFalse(gate.consume(1200,true,true,true,true,true));
    }
    @Test public void leavingAndReturningCannotRedeemOldToken() {
        DashStartTokenGate gate = new DashStartTokenGate(1000);gate.cancel();
        assertFalse(gate.consume(1100,true,true,true,true,true));
    }
    @Test public void wrongCreatorMutableOrWrongTypeCannotStart() {
        assertFalse(new DashStartTokenGate(1000).consume(1100,false,true,true,true,true));
        assertFalse(new DashStartTokenGate(1000).consume(1100,true,false,true,true,true));
    }
    @Test public void hiddenPausedOrPermissionLostCannotStart() {
        assertFalse(new DashStartTokenGate(1000).consume(1100,true,true,false,true,true));
        assertFalse(new DashStartTokenGate(1000).consume(1100,true,true,true,false,true));
        assertFalse(new DashStartTokenGate(1000).consume(1100,true,true,true,true,false));
    }
    @Test public void delayedAndBackwardTimeRepliesCannotStart() {
        assertFalse(new DashStartTokenGate(1000).consume(11000,true,true,true,true,true));
        assertFalse(new DashStartTokenGate(1000).consume(999,true,true,true,true,true));
    }
    @Test public void failedAttemptCannotBeRetriedWithoutFreshAction() {
        DashStartTokenGate gate = new DashStartTokenGate(1000);
        assertFalse(gate.consume(1100,true,true,false,true,true));
        assertFalse(gate.consume(1200,true,true,true,true,true));
    }
}
