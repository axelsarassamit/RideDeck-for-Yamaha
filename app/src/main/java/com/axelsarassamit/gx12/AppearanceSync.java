package com.axelsarassamit.gx12;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Appearance is sent only to the authenticated Dash installation, never via launch extras. */
public final class AppearanceSync {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private AppearanceSync() { }
    public static void send(Context context) {
        Context app=context.getApplicationContext();
        WORKER.execute(()->{
            try {
                if(!DashCompanion.installed(app)) return;
                Bundle extras=new Bundle();extras.putInt("protocolVersion",1);
                extras.putInt("appearanceMode",RideAppearance.mode(app));
                extras.putInt("appearanceAccent",RideAppearance.accentIndex(app));
                deliver(app.getContentResolver(),Uri.parse("content://" + DashCompanion.PACKAGE + ".bridge"),extras);
            } catch(RuntimeException ignored) { /* Older or absent Dash keeps working without appearance sync. */ }
        });
    }
    /** An optional peer dying must not make Android terminate this host as a stable dependent. */
    static boolean deliver(android.content.ContentResolver resolver,Uri endpoint,Bundle extras) {
        try(android.content.ContentProviderClient client=resolver.acquireUnstableContentProviderClient(endpoint)) {
            if(client==null)return false;
            client.call("appearance",null,extras);return true;
        } catch(android.os.RemoteException|RuntimeException unavailable) { return false; }
    }
}
