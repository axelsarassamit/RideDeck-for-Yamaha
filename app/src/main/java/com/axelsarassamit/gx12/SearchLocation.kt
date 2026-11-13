package com.axelsarassamit.gx12

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

/** Read already-available positions only; searching never starts riding/background location. */
object SearchLocation {
    private fun permitted(c:Context)=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun precise(c:Context)=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun quality(l:Location)=NavigationFix(l.latitude,l.longitude,if(l.hasAccuracy())l.accuracy.toDouble() else Double.NaN,l.elapsedRealtimeNanos,l.provider==LocationManager.GPS_PROVIDER)
    fun snapshot(c:Context,navigation:Location?):Location? {
        if(!permitted(c))return null
        val fine=precise(c)
        val live=navigation?.let(::Location)?.takeIf{
            SearchLocationPolicy.choose(quality(it),emptyList(),SystemClock.elapsedRealtimeNanos(),fine)!=null
        }
        if(live!=null)return live
        val manager=c.getSystemService(LocationManager::class.java) ?: return null
        val providers=if(fine)listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
        val candidates=providers.mapNotNull{provider->
            try { manager.getLastKnownLocation(provider)?.let(::Location) }
            catch(_:SecurityException){null} // Permission can change after the initial check.
            catch(_:IllegalArgumentException){null} // A device may omit one of the providers.
        }
        val selected=SearchLocationPolicy.choose(null,candidates.map(::quality),SystemClock.elapsedRealtimeNanos(),fine) ?: return null
        return candidates.firstOrNull{quality(it)==selected}?.takeIf{permitted(c)&&(!fine||precise(c))}
    }
}
