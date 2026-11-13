package com.axelsarassamit.gx12
import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppearanceSyncFailureTest {
    @Test fun optionalPeerDeathDoesNotTerminateTheHost() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        check(c.packageName.endsWith(".preview"))
        val endpoint=Uri.parse("content://com.axelsarassamit.gx12.yamaha.preview.test.appearance_fault")
        val probe=c.contentResolver.acquireUnstableContentProviderClient(endpoint)
        assertNotNull("Fault provider must actually be reachable",probe)
        val peer=try{probe!!.call("probe",null,Bundle())!!.getInt("pid")}finally{probe!!.close()}
        assertTrue(peer>0&&peer!=android.os.Process.myPid())
        assertFalse(AppearanceSync.deliver(c.contentResolver,endpoint,Bundle()))
        assertTrue("Caller remains alive after peer death",android.os.Process.myPid()>0)
    }
}
