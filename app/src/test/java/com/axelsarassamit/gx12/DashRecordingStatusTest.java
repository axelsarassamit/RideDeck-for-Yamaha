package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashRecordingStatusTest {
    @Test public void requestedStatusIsNotAConfirmedCameraSample() {
        DashRecordingStatus status = new DashRecordingStatus();status.requested(1000);
        assertFalse(status.fresh(1001));status.confirmed(false,true,1100);
        assertTrue(status.fresh(1100));assertFalse(status.fresh(4100));
        status.unavailable();assertFalse(status.fresh(1101));
        status.confirmed(true,false,1200);status.clear();assertFalse(status.fresh(1201));
    }
    @Test public void olderStatusCannotOverwriteNewerPreviewRecording() {
        DashRecordingStatus status = new DashRecordingStatus();status.requested(1000);
        assertTrue(status.confirmedSample(3,true,false,1100));
        assertFalse(status.confirmedSample(2,false,false,1200));
        assertEquals(DashRecordingStatus.State.RECORDING,status.state(1201));
    }
    @Test public void staleTimeoutCannotEraseFreshPreviewButNewFailureCan() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmedSample(3,true,false,1100);
        status.unavailableSample(2);assertEquals(DashRecordingStatus.State.RECORDING,status.state(1200));
        status.unavailableSample(4);assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(1201));
    }
    @Test public void newRideResetsSampleOrderAndStillExpiresActualRec() {
        DashRecordingStatus status = new DashRecordingStatus();status.confirmedSample(50,true,false,1000);
        status.clear();assertTrue(status.confirmedSample(1,true,false,2000));
        assertEquals(DashRecordingStatus.State.UNAVAILABLE,status.state(5000));
    }
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
