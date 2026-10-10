package com.axelsarassamit.gx12;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provider calls are serialized off the UI thread. No camera or recording calls. */
public final class DashCompanion {
    public static final String PACKAGE = "com.axelsarassamit.ridedeck.dash";
    private static final String SIGNER = "cef073342e133ad4c6650da1a276ae4ad03e3396cecac67143154aeb6e6c731b";
    private static final Uri BRIDGE = Uri.parse("content://" + PACKAGE + ".bridge");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private DashCompanion() { }
    public static boolean installed(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            if (manager.getLaunchIntentForPackage(PACKAGE) == null) return false;
            byte[] expected = new byte[SIGNER.length() / 2];
            for (int i = 0; i < expected.length; i++) expected[i] = (byte) Integer.parseInt(SIGNER.substring(i * 2, i * 2 + 2), 16);
            if (Build.VERSION.SDK_INT >= 28) return manager.hasSigningCertificate(PACKAGE, expected, PackageManager.CERT_INPUT_SHA256);
            PackageInfo info = manager.getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES);
            return info.signatures != null && info.signatures.length == 1 && java.security.MessageDigest.isEqual(expected,
                java.security.MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray()));
        } catch (PackageManager.NameNotFoundException | java.security.NoSuchAlgorithmException | RuntimeException error) { return false; }
    }
    public static void open(Activity activity) {
        if (!installed(activity)) return;
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
