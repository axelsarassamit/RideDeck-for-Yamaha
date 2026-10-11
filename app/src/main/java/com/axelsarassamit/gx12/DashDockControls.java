package com.axelsarassamit.gx12;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared Ride timer/Pause and optional Dash tile styling in both host apps. */
final class DashDockControls {
    private DashDockControls() { }
    static LinearLayout rideTile(Context context, TextView clock, TextView toggle, boolean compact) {
        LinearLayout tile = new LinearLayout(context);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(context,2),dp(context,2),dp(context,2),dp(context,2));tile.setBackground(background(context,0xff202820,12));
        clock.setTextColor(0xffc8d3df);clock.setTextSize(compact ? 10 : 12);clock.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);clock.setContentDescription("Ride session timer");
        tile.addView(clock,new LinearLayout.LayoutParams(-1,dp(context,compact ? 21 : 24)));
        toggle.setTextColor(0xfff4f6fa);toggle.setTextSize(compact ? 11 : 12);toggle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        toggle.setGravity(Gravity.CENTER);toggle.setSingleLine(true);toggle.setPadding(dp(context,2),0,dp(context,2),0);
        toggle.setBackground(background(context,0xff343d34,9));toggle.setClickable(true);toggle.setFocusable(true);
        tile.addView(toggle,new LinearLayout.LayoutParams(-1,0,1));return tile;
    }
    static Button dashButton(Context context) {
        Button button = new Button(context);button.setText("Dash");button.setAllCaps(false);button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setMinWidth(0);button.setMinimumWidth(0);
        button.setMinHeight(dp(context,56));button.setMinimumHeight(dp(context,56));button.setPadding(dp(context,4),dp(context,4),dp(context,4),dp(context,4));
        button.setMaxLines(1);button.setBackgroundTintList(null);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8191a1,0xfff4f6fa}));
        StateListDrawable states = new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},background(context,0xff252a25,12));
        states.addState(new int[]{android.R.attr.state_pressed},background(context,0xff4b554b,12));states.addState(new int[]{},background(context,0xff2c322c,12));
        button.setBackground(states);return button;
    }
    static LinearLayout.LayoutParams tileParams(Context context, boolean compact) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(context,compact ? 56 : 64),1);
        p.setMargins(dp(context,3),dp(context,4),dp(context,3),0);return p;
    }
    static String elapsedLabel(long millis) {
        long seconds = Math.max(0,millis / 1000);
        return seconds >= 3600 ? String.format(java.util.Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
            : String.format(java.util.Locale.US,"%02d:%02d",seconds/60,seconds%60);
    }
    private static GradientDrawable background(Context context, int color, int radius) {
        GradientDrawable result = new GradientDrawable();result.setColor(color);result.setCornerRadius(dp(context,radius));return result;
    }
    private static int dp(Context context, int n) { return (int)(n*context.getResources().getDisplayMetrics().density+0.5f); }
}
