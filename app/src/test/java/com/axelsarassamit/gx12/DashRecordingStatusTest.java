package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashRecordingStatusTest {
    @Test public void leaseAndRequestCannotClaimRecording() {
        DashRecordingStatus status = new DashRecordingStatus();
        assertEquals(DashRecordingStatus.State.IDLE,status.state(0));
        status.requested(1000);assertEquals(DashRecordingStatus.State.STARTING,status.state(1100));
    }
    @Test public void onlyFreshActualCaptureShowsRec() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmed(true,false,1000);
        assertEquals(DashRecordingStatus.State.RECORDING,status.state(3999));
        assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(4000));
        status.confirmed(false,false,4001);assertEquals(DashRecordingStatus.State.STOPPED,status.state(4002));
    }
    @Test public void startingAndFailureAreDistinctFromRec() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmed(false,true,1000);
        assertEquals(DashRecordingStatus.State.STARTING,status.state(1000));
        status.unavailable();assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(1001));
    }
    @Test public void leavingAndReturningCannotReuseAnOldRecState() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmed(true,false,1000);
        assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(6000));
        status.clear();assertEquals(DashRecordingStatus.State.IDLE,status.state(6001));
    }
    @Test public void backwardClockCannotMakeOldRecordingFresh() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmed(true,false,1000);
        assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(999));
    }
}
