package com.axelsarassamit.gx12;

import android.content.pm.ActivityInfo;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FixedGuidanceTest {
    private static void settle() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(450);
    }
    @Test public void longRoadNamesAndMissingSpeedSignsKeepCardBounds() throws Exception {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=RidePreferences.prefs(c);
        boolean existed=prefs.contains("phone_panel");String previous=prefs.getString("phone_panel","music");
        prefs.edit().putString("phone_panel","music").commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                scenario.onActivity(a->a.setRequestedOrientation(orientation));settle();
                final TextView[] card={null};final SpeedLimitIcon[] sign={null};final int[] size=new int[2];final float[] textSize={0};
                scenario.onActivity(a->{try {
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("phoneGuidance");field.setAccessible(true);
                    card[0]=(TextView)field.get(a);assertNotNull(card[0]);
                    // Isolate measurement from live navigation's refresh timer.
                    field.set(a,null);((View)card[0].getParent()).setVisibility(View.VISIBLE);
                    java.lang.reflect.Field speed=MainActivity.class.getDeclaredField("phoneSpeedLimit");speed.setAccessible(true);sign[0]=(SpeedLimitIcon)speed.get(a);
                    card[0].setText("90 m Turn left");sign[0].setLimit(null);
                }catch(Exception e){throw new AssertionError(e);}});settle();
                scenario.onActivity(a->{size[0]=card[0].getWidth();size[1]=card[0].getHeight();textSize[0]=card[0].getTextSize();
                    card[0].setText("90 m Turn left onto Thanon Sukhumvit Road, Samut Prakan intersection");sign[0].setLimit(80);});settle();
                scenario.onActivity(a->{assertEquals(size[0],card[0].getWidth());assertEquals(size[1],card[0].getHeight());
                    assertTrue("Long guidance must shrink",card[0].getTextSize()<textSize[0]);
                    assertNotNull(card[0].getLayout());
                    assertEquals("Entire instruction must fit",card[0].getText().length(),card[0].getLayout().getLineEnd(card[0].getLayout().getLineCount()-1));
                    sign[0].setLimit(null);});settle();
                scenario.onActivity(a->{assertEquals(size[0],card[0].getWidth());assertEquals(size[1],card[0].getHeight());});
            }
        } finally {if(existed)prefs.edit().putString("phone_panel",previous).commit();else prefs.edit().remove("phone_panel").commit();}
    }
}
