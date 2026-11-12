package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast

/** Explicit workshop handoff. The Lab chooses and starts its own Bluetooth session. */
object YamahaLabLauncher {
    @JvmStatic fun open(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage("app.yamahalab")
        if (launch == null) {
            Toast.makeText(activity, "Install Yamaha Bluetooth Lab to run bike tests.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity).setTitle("Open Yamaha Bluetooth Lab")
            .setMessage("Run tests while parked. Disconnect bike data in RideDeck and any other app before connecting in the Lab.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Lab") { _, _ ->
                try {
                    activity.startActivity(launch)
                } catch (_: RuntimeException) {
                    Toast.makeText(activity, "Yamaha Bluetooth Lab could not open.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}
