package com.axelsarassamit.gx12;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Lifecycle-bound header clock using live route progress, never placeholder ETA or distance. */
public final class CockpitClockView extends LinearLayout {
    private final TextView caption, value;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long startedAt, manualOffset;
    private boolean attached;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); if (attached) main.postDelayed(this, 1000); }
    };
    public CockpitClockView(Context context) {
        super(context); setOrientation(VERTICAL); setGravity(Gravity.CENTER);
        caption = new TextView(context); caption.setTextColor(RideStyle.MUTED); caption.setTextSize(10);
        caption.setGravity(Gravity.CENTER); caption.setIncludeFontPadding(false); caption.setSingleLine(true);
        caption.setLetterSpacing(0.08f); addView(caption, new LayoutParams(-1, -2));
        value = new TextView(context); value.setTextColor(RideStyle.TEXT);
        value.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        value.setGravity(Gravity.CENTER); value.setIncludeFontPadding(false); value.setSingleLine(true);
        value.setAutoSizeTextTypeUniformWithConfiguration(18,36,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        addView(value, new LayoutParams(-1,0,1));
        setFocusable(true); setOnClickListener(v -> { manualOffset += CockpitClockCycle.INTERVAL_MILLIS; update(); });
        startedAt=SystemClock.elapsedRealtime(); update();
    }
    private void update() {
        long now=System.currentTimeMillis();
        CockpitTrip trip=NativeNavigation.cockpitTrip();
        Long arrival=trip == null ? null : trip.getArrivalMillis();
        CockpitClockCycle.Mode mode=CockpitClockCycle.mode(SystemClock.elapsedRealtime()-startedAt+manualOffset,arrival!=null,trip!=null);
        String label="TIME", reading=android.text.format.DateFormat.getTimeFormat(getContext()).format(new Date(now));
        if (mode == CockpitClockCycle.Mode.ETA) {
            label="ETA";
            Calendar today=Calendar.getInstance(), target=Calendar.getInstance();
            today.setTimeInMillis(now); target.setTimeInMillis(arrival);
            if (today.get(Calendar.YEAR)!=target.get(Calendar.YEAR) || today.get(Calendar.DAY_OF_YEAR)!=target.get(Calendar.DAY_OF_YEAR))
                label += " · " + new java.text.SimpleDateFormat("EEE",Locale.getDefault()).format(new Date(arrival));
            reading=android.text.format.DateFormat.getTimeFormat(getContext()).format(new Date(arrival));
        } else if (mode == CockpitClockCycle.Mode.DISTANCE) {
            label="KM TO GO";
            double kilometers=trip.getRemainingMeters()/1000.0;
            reading=String.format(Locale.getDefault(),kilometers<1 ? "%.2f" : "%.1f",kilometers);
        }
        caption.setText(label); value.setText(reading);
        setContentDescription((mode==CockpitClockCycle.Mode.DISTANCE ? "Kilometers remaining " : label+" ")+reading+". Tap to switch.");
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached=true; main.removeCallbacks(refresh); main.post(refresh);
    }
    @Override protected void onDetachedFromWindow() {
        attached=false; main.removeCallbacks(refresh); super.onDetachedFromWindow();
    }
}
