package com.axelsarassamit.gx12

/** Search proximity is less strict than riding guidance, but never future, stale or unbounded. */
object SearchLocationPolicy {
    fun valid(fix:NavigationFix,now:Long)=fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
        fix.accuracy.isFinite() && fix.accuracy in 0.0..1000.0 && now-fix.atNanos in 0..60_000_000_000L
    fun choose(navigation:NavigationFix?,known:List<NavigationFix>,now:Long,preciseAllowed:Boolean=true):NavigationFix? =
        navigation?.takeIf{preciseAllowed&&valid(it,now)} ?: known.filter{valid(it,now)}
            .sortedWith(compareBy<NavigationFix>{it.accuracy}.thenByDescending{it.atNanos}).firstOrNull()
}
