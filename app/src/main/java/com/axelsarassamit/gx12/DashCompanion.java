package com.axelsarassamit.gx12;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provider calls are serialized off the UI thread. No camera or recording calls. */
public final class DashCompanion {
    public static final String PACKAGE = "com.axelsarassamit.ridedeck.dash";
    private static final Uri BRIDGE = Uri.parse("content://" + PACKAGE + ".bridge");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private DashCompanion() { }
    public static boolean installed(Context context) { return context.getPackageManager().getLaunchIntentForPackage(PACKAGE) != null; }
    public static void open(Activity activity) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (launch == null) { Toast.makeText(activity, "RideDeck Dash is not installed", Toast.LENGTH_LONG).show(); return; }
        try { activity.startActivity(launch); }
        catch (RuntimeException error) { Toast.makeText(activity, "RideDeck Dash could not open", Toast.LENGTH_LONG).show(); }
    }
    public static void renew(Context context, java.util.function.BooleanSupplier stillActive, java.util.function.Consumer<String> completion) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            if (!stillActive.getAsBoolean()) { completion.accept(null); return; }
            completion.accept(call(app, "renew"));
        });
    }
    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        // Queued after any outstanding renewal; the provider clears only this UID's lease.
        WORKER.execute(() -> { String result = call(app, "stop"); BikeDiagnostics.record(app, "Dash lease stop result=" + result); });
    }
    private static String call(Context app, String method) {
        try {
            if (!installed(app)) return "RideDeck Dash is not installed";
            Bundle extras = new Bundle(); extras.putInt("protocolVersion", 1);
            Bundle reply = app.getContentResolver().call(BRIDGE, method, null, extras);
            if (reply == null) return "Dash connection unavailable";
            if ("renew".equals(method)) return DashBridgeReply.renewal(reply.getInt("protocolVersion", 0),
                reply.containsKey("accepted") ? reply.getBoolean("accepted") : null, reply.getBoolean("rideActive", false));
            return reply.getInt("protocolVersion", 0) == 1 ? "stopped" : "Dash needs a compatible update";
        } catch (SecurityException error) { return "Dash did not authorize this RideDeck update"; }
        catch (IllegalArgumentException error) { return "Dash needs a compatible update"; }
        catch (RuntimeException error) { return "Dash connection unavailable"; }
    }
}
