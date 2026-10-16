package com.axelsarassamit.gx12;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public final class DesignControlsTest {
    private static void find(android.view.View v,java.util.List<android.widget.Button> found){if(v instanceof android.widget.Button && v.isShown())found.add((android.widget.Button)v);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)find(g.getChildAt(i),found);}}
    @Test public void primaryActionsStayVisibleInBothOrientations() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("Unlock phone before visual checks",c.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked());
        try(ActivityScenario<Activity> scenario=ActivityScenario.launch(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))){
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}){
                scenario.onActivity(a->{a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);a.setRequestedOrientation(orientation);});
                Thread.sleep(1200);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->{assertTrue(a.hasWindowFocus());
                    java.util.List<android.widget.Button> buttons=new java.util.ArrayList<>();find(a.getWindow().getDecorView(),buttons);
                    for(String label:new String[]{"Camera","Voice"}) {
                        android.widget.Button b=buttons.stream().filter(v->label.equals(v.getContentDescription())).findFirst().orElseThrow(()->new AssertionError("Missing "+label));
                        android.graphics.Rect visible=new android.graphics.Rect();assertTrue(label,b.getGlobalVisibleRect(visible));
                        assertTrue(label+" height",visible.height()>=Math.round(52*a.getResources().getDisplayMetrics().density));
                    }
});
            }
        }
    }
}
