package com.axelsarassamit.gx12

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File

/** Explicit own-app diagnostic export. No settings, keys, message data or captured Bluetooth credentials. */
class IncidentExportTest {
    @Test fun exportConnectionMetadata() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(c.getExternalFilesDir("tests"),"connection-incident").apply { mkdirs() }
        for(name in listOf("bike-diagnostics.previous.txt","bike-diagnostics.txt","ccu-parked-test.previous.jsonl","ccu-parked-test.jsonl")) {
            val file=File(c.filesDir,name)
            if(file.exists())file.copyTo(File(folder,name),true)
        }
    }
}
