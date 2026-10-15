package com.axelsarassamit.gx12;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceResumePolicyTest {
    @Test public void returnToSameActiveRideMayResume(){assertTrue(VoiceResumePolicy.allow(true,true,true,7,7));}
    @Test public void pauseEndOrNewRideCannotRestartRecording(){
        assertFalse(VoiceResumePolicy.allow(true,true,false,7,7));
        assertFalse(VoiceResumePolicy.allow(true,true,true,7,8));
    }
    @Test public void backgroundAndUnrelatedVoiceCannotStartCamera(){
        assertFalse(VoiceResumePolicy.allow(true,false,true,7,7));
        assertFalse(VoiceResumePolicy.allow(false,true,true,7,7));
    }
}
