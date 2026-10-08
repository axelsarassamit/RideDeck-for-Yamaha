package com.axelsarassamit.gx12;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Preserve the Motor emblem from the repository's original artwork, rather than substitute the Corporation emblem. */
public final class YamahaBranding {
    private static Bitmap emblem;
    private YamahaBranding() { }
    public static synchronized Drawable roundLogo(Context context) {
        if (emblem == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.yamaha_motor_logo, options);
            // The untouched Motor artwork places its complete round emblem in this square.
            emblem = Bitmap.createBitmap(original, 0, 0, 224, 224);
        }
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), emblem);
        drawable.setFilterBitmap(true);
        drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        return drawable;
    }
}
