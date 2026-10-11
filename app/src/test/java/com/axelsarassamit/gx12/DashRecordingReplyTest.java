package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashRecordingReplyTest {
    @Test public void acceptedRequestNeverClaimsCameraState() {
        assertEquals("Recording requested. Check the live camera view.", DashRecordingReply.status(1, true, null));
    }
    @Test public void unsupportedOrMissingAcknowledgementFailsClosed() {
        assertFalse(DashRecordingReply.valid(2,true));assertFalse(DashRecordingReply.valid(1,null));
        assertEquals("Dash needs a compatible update",DashRecordingReply.status(2,true,"Recording"));
    }
    @Test public void setupErrorsAreVisibleWithoutLaunchingAnything() {
        assertEquals("Open Dash once for camera permissions",DashRecordingReply.status(1,false,"Open Dash once for camera permissions"));
        assertTrue(DashRecordingReply.status(1,false,null).contains("not accepted"));
    }
    @Test public void previewPayloadMustBePresentAndBounded() {
        assertFalse(DashRecordingReply.frameAllowed(null));assertFalse(DashRecordingReply.frameAllowed(new byte[0]));
        assertTrue(DashRecordingReply.frameAllowed(new byte[100000]));assertFalse(DashRecordingReply.frameAllowed(new byte[100001]));
    }
    @Test public void messageLengthIsBoundedForGloveReadableStatus() {
        assertEquals("Recording unavailable. Check Dash settings and try Start again.",
            DashRecordingReply.status(1,true,new String(new char[500]).replace('\0','x')));
    }
    @Test public void androidFailureDetailsDoNotCoverThePreview() {
        assertEquals("Android blocked recording. Keep RideDeck visible and tap Start again.",
            DashRecordingReply.displayMessage("java.lang.SecurityException: Starting FGS with type microphone uid=10345 targetSDK=35 requires permissions"));
    }
    @Test public void shortRiderMessagesStayReadableAndMissingStatusIsClear() {
        assertEquals("Starting cameras",DashRecordingReply.displayMessage("  Starting\n cameras  "));
        assertEquals("No live image. Check Dash settings.",DashRecordingReply.displayMessage(null));
    }
    @Test public void missingOrContradictoryCameraStateCannotConfirmRec() {
        assertFalse(DashRecordingReply.cameraStateValid(null,false));
        assertFalse(DashRecordingReply.cameraStateValid(true,null));
        assertFalse(DashRecordingReply.cameraStateValid(true,true));
        assertTrue(DashRecordingReply.cameraStateValid(true,false));
        assertTrue(DashRecordingReply.cameraStateValid(false,false));
        assertTrue(DashRecordingReply.cameraStateValid(false,true));
    }
}
