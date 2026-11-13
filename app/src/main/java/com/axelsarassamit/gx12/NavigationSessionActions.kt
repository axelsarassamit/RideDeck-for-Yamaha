package com.axelsarassamit.gx12

import android.app.Activity
import android.content.Intent
import android.widget.Toast

/** Shared meanings for the flat map settings and riding action menus. */
object NavigationSessionActions {
    fun clearRoute(a:Activity) {
        NativeNavigation.stopRoute()
        Toast.makeText(a,if(NativeNavigation.running)"Route cleared. Free-ride map stays on." else "Route cleared.",Toast.LENGTH_SHORT).show()
    }
    fun stopNavigation(a:Activity) {
        a.startService(Intent(a,NativeNavigationService::class.java).setAction("STOP"))
        Toast.makeText(a,"Navigation stopped",Toast.LENGTH_SHORT).show()
    }
}
