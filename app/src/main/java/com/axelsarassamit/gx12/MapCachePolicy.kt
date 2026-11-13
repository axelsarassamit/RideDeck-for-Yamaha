package com.axelsarassamit.gx12

/** Bounded personal cache; explicit server prohibitions always win. This is not bulk download. */
object MapCachePolicy {
    fun lifetimeMillis(cacheControl:String?):Long {
        val parts=cacheControl?.lowercase(java.util.Locale.ROOT)?.split(',')?.map{it.trim()} ?: emptyList()
        if(parts.any{it=="no-store" || it=="no-cache" || it.startsWith("no-cache=")})return 0
        val age=parts.firstOrNull{it.startsWith("max-age=")}
        if(age==null)return if(parts.any{it=="must-revalidate"||it=="proxy-revalidate"})0 else 3_600_000L
        val seconds=age.substringAfter('=').trim('"').toLongOrNull() ?: return 0
        return seconds.coerceIn(0,86400)*1000
    }
    fun reusable(identity:String, expected:String, savedAt:Long, expiresAt:Long, now:Long)=
        identity==expected && savedAt<=now && expiresAt>now && expiresAt-savedAt in 1..86_400_000L
    fun diskBudget(availableBytes:Long):Long = (if(availableBytes<1024L*1024*1024)64L else 256L)*1024*1024
}
