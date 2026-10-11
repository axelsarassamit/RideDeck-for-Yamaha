package com.axelsarassamit.gx12;

import android.app.Activity;
import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One visible rider gesture binds Dash; the explicit ride owns its lifetime. Never reconnects itself. */
public final class DashRecordingConnection implements ServiceConnection {
    private static final String SERVICE = DashCompanion.PACKAGE + ".CompanionRecordingService";
    private static DashRecordingConnection current;
    private final Context app;
    private final long generation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BooleanSupplier visible;
    private Messenger remote;
    private final Messenger replies;
    private Consumer<Bundle> preview;
    private boolean bound, sent, accepted, closed, previewPending;
    private int expectedUid;

    private DashRecordingConnection(Activity activity, long generation, BooleanSupplier visible) {
        app = activity.getApplicationContext(); this.generation = generation; this.visible = visible;
        replies = new Messenger(new Handler(Looper.getMainLooper(), this::reply));
    }
    public static boolean permissionsGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    public static void start(Activity activity, long generation, BooleanSupplier visible) {
        close();
        if (!visible.getAsBoolean() || !DashRideService.active() || DashRideService.generation() != generation || !DashCompanion.installed(activity)) return;
        if (!permissionsGranted(activity)) { DashRideService.recordingStatus = "Allow camera and microphone in Yamaha's Android permissions, then start again.";return; }
        DashRecordingConnection connection = new DashRecordingConnection(activity, generation, visible);
        current = connection;
        DashRideService.recordingStatus = "Connecting recording. Keep this screen open.";
        try {
            connection.expectedUid = connection.app.getPackageManager().getApplicationInfo(DashCompanion.PACKAGE, 0).uid;
            int flags = Context.BIND_AUTO_CREATE;
            if (Build.VERSION.SDK_INT >= 30) flags |= Context.BIND_INCLUDE_CAPABILITIES;
            connection.bound = true;
            boolean binding = connection.app.bindService(new Intent().setComponent(new ComponentName(DashCompanion.PACKAGE, SERVICE)), connection, flags);
            if (!binding) { connection.fail("Update Dash to record here. Use Dash settings for setup."); return; }
            connection.main.postDelayed(connection.startTimeout, 10000);
        } catch (Exception error) { connection.fail("Dash recording connection unavailable. Check Dash settings."); }
    }
    public static void close() { if (current != null) current.release(); }
    public static void cancelPending() { if (current != null && !current.sent) current.fail("Recording request cancelled before connection."); }
    public static boolean preview(long generation, Consumer<Bundle> completion) {
        DashRecordingConnection c = current;
        if (c == null || c.closed || !c.accepted || c.remote == null || c.previewPending || !c.active() || c.generation != generation) return false;
        c.preview = completion;c.previewPending = true;
        try {
            c.send(2);
            c.main.postDelayed(c.previewTimeout, 1000);
            return true;
        } catch (RemoteException error) { c.fail("Dash connection lost. Check recording in Dash."); return false; }
    }
    public static void stopPreview() { if (current != null) { current.main.removeCallbacks(current.previewTimeout);current.preview = null; } }
    private boolean active() { return current == this && !closed && DashRideService.active() && DashRideService.generation() == generation; }
    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
        if (!active() || visible == null || !visible.getAsBoolean() || !SERVICE.equals(name.getClassName()) || !DashCompanion.installed(app) || !permissionsGranted(app)) {
            fail("Recording request cancelled. Start again from RideDeck."); return;
        }
        remote = new Messenger(binder);
        try { send(1);sent = true;visible = null; }
        catch (RemoteException error) { fail("Recording request could not reach Dash."); }
    }
    private void send(int what) throws RemoteException {
        Message request = Message.obtain(null, what);Bundle data = new Bundle();data.putInt("protocolVersion", 1);
        request.setData(data);request.replyTo = replies;remote.send(request);
    }
    private boolean reply(Message reply) {
        if (!active() || reply.sendingUid != expectedUid) return true;
        Bundle data = reply.getData();
        Boolean allowed = data.containsKey("accepted") ? data.getBoolean("accepted") : null;
        if (reply.what == 1 && sent && !accepted) {
            main.removeCallbacks(startTimeout);
            DashRideService.recordingStatus = DashRecordingReply.status(data.getInt("protocolVersion"), allowed, data.getString("message"));
            accepted = DashRecordingReply.valid(data.getInt("protocolVersion"), allowed) && Boolean.TRUE.equals(allowed);
            if (!accepted) release();
        } else if (reply.what == 2 && previewPending) {
            main.removeCallbacks(previewTimeout);Consumer<Bundle> completion = preview;preview = null;previewPending = false;
            if (completion != null) completion.accept(DashRecordingReply.valid(data.getInt("protocolVersion"), allowed) && Boolean.TRUE.equals(allowed) ? data : null);
        }
        return true;
    }
    private final Runnable startTimeout = () -> fail("Recording request not confirmed. Check Dash settings.");
    private final Runnable previewTimeout = () -> { Consumer<Bundle> completion = preview;preview = null;if (completion != null) completion.accept(null); };
    private void fail(String message) { if (closed) return;DashRideService.recordingStatus = message;release(); }
    private void release() {
        if (closed) return;
        closed = true;visible = null;remote = null;preview = null;previewPending = false;main.removeCallbacksAndMessages(null);
        if (bound) { bound = false;try { app.unbindService(this); } catch (IllegalArgumentException ignored) { } }
        if (current == this) current = null;
    }
    @Override public void onServiceDisconnected(ComponentName name) { fail("Dash connection lost. Check recording in Dash."); }
    @Override public void onBindingDied(ComponentName name) { fail("Dash changed. Start again from RideDeck."); }
    @Override public void onNullBinding(ComponentName name) { fail("Update Dash to record from RideDeck."); }
}
