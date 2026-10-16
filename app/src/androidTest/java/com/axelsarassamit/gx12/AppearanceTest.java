package com.axelsarassamit.gx12;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;

/** Reversible checks on real app screens; screenshots remain in the app's private test export folder. */
public final class AppearanceTest {
    @Test public void authenticatedDashSyncKeepsModeAccentAndRecordingLeaseIndependent() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Install the signed Dash companion before this integration check",DashCompanion.installed(c));
        android.net.Uri uri=android.net.Uri.parse("content://com.axelsarassamit.ridedeck.dash.bridge");
        Bundle request=new Bundle();request.putInt("protocolVersion",1);
        Bundle before=c.getContentResolver().call(uri,"status",null,request);assertNotNull(before);
        assertTrue("Update Dash before checking appearance",before.containsKey("appearanceHostMode"));
        try {
            for(int mode:new int[]{0,1,2}) for(int accent=0;accent<5;accent++) {
                request.putInt("appearanceMode",mode);request.putInt("appearanceAccent",accent);
                Bundle accepted=c.getContentResolver().call(uri,"appearance",null,request);
                assertNotNull(accepted);assertTrue(accepted.getBoolean("accepted"));
                Bundle state=c.getContentResolver().call(uri,"status",null,request);assertNotNull(state);
                assertEquals(mode,state.getInt("appearanceHostMode"));assertEquals(accent,state.getInt("appearanceHostAccent"));
                if(state.getBoolean("appearanceFollowsHost")) {
                    assertEquals(mode,state.getInt("appearanceMode"));assertEquals(accent,state.getInt("appearanceAccent"));
                }
                assertEquals(before.getBoolean("rideActive"),state.getBoolean("rideActive"));
            }
            request.putInt("appearanceMode",3);
            try {c.getContentResolver().call(uri,"appearance",null,request);fail("Invalid appearance accepted");}
            catch(IllegalArgumentException expected) { }
        } finally {
            request.putInt("appearanceMode",before.getInt("appearanceHostMode"));
            request.putInt("appearanceAccent",before.getInt("appearanceHostAccent"));
            c.getContentResolver().call(uri,"appearance",null,request);
        }
    }
    @Test public void everyAccentHasNativeLightAndDarkScreensInBothOrientations() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("Unlock the phone before visual checks",c.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked());
        SharedPreferences p=RideAppearance.prefs(c);
        java.util.Map<String,?> before=new java.util.HashMap<>(p.getAll());
        try {
            for(int mode:new int[]{1,2}) for(int accent=0;accent<5;accent++) {
                p.edit().putBoolean("follow_host",false).putInt("appearance_mode",mode).putInt("color_theme",accent).commit();
                boolean dark=mode==2;int expectedAccent=AppearancePalette.accent(accent,dark);
                Intent intent=new Intent(c,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try(ActivityScenario<Activity> scenario=ActivityScenario.launch(intent)) {
                    for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                        scenario.onActivity(a->a.setRequestedOrientation(orientation));
                        Thread.sleep(900);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        scenario.onActivity(a->{
                            assertEquals(dark,RideAppearance.isDark(a));
                            assertEquals(AppearancePalette.background(dark),RideStyle.BACKGROUND);
                            assertEquals(AppearancePalette.text(dark),RideStyle.TEXT);
                            TypedValue colour=new TypedValue();assertTrue(a.getTheme().resolveAttribute(android.R.attr.colorAccent,colour,true));
                            assertEquals(expectedAccent,colour.data);
                            assertEquals(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?android.content.res.Configuration.ORIENTATION_PORTRAIT:android.content.res.Configuration.ORIENTATION_LANDSCAPE,a.getResources().getConfiguration().orientation);
                        });
                        Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(shot);
                        File dir=new File(c.getExternalFilesDir(null),"tests/appearance");assertTrue(dir.isDirectory()||dir.mkdirs());
                        try(FileOutputStream out=new FileOutputStream(new File(dir,"mode"+mode+"-accent"+accent+"-orientation"+orientation+".png"))) { assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,out)); } finally {shot.recycle();}
                    }
                }
            }
        } finally {
            SharedPreferences.Editor edit=p.edit();
            for(String key:new String[]{"follow_host","appearance_mode","color_theme"}) {
                Object value=before.get(key);if(value==null)edit.remove(key);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else edit.putInt(key,(Integer)value);
            }
            edit.commit();RideAppearance.update(c);AppearanceSync.send(c);
        }
    }
}
