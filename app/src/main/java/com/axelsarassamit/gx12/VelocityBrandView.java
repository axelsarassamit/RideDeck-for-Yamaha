package com.axelsarassamit.gx12;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Approved Canva artwork, drawn with transparent margins and a readable light treatment. */
public final class VelocityBrandView extends View {
    private final Bitmap darkBitmap, lightBitmap;
    private final Paint ink = new Paint(3);
    public VelocityBrandView(Context context) {
        super(context);
        darkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.velocity_logo);
        lightBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.velocity_logo_light);
        setContentDescription("RideDeck for Yamaha logo");
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Only RideDeck's ink changes with the theme. Yamaha's original red artwork is never tinted.
        Bitmap bitmap=RideAppearance.isDark(getContext())?darkBitmap:lightBitmap;
        float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        canvas.drawBitmap(bitmap,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),ink);
    }
}
