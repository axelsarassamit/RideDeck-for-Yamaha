package app.pillion.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

/** Offline NOAA fractional-year solar position; sunrise/sunset zenith is 90.833 degrees. */
object SolarMapTheme {
    fun darkAt(nowMillis: Long, latitude: Double, longitude: Double): Boolean? {
        if(!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val utc=Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
        val hour=utc.hour+utc.minute/60.0+utc.second/3600.0+utc.nano/3_600_000_000_000.0
        val yearAngle=2*PI/utc.toLocalDate().lengthOfYear()*(utc.dayOfYear-1+(hour-12)/24)
        val equation=229.18*(0.000075+0.001868*cos(yearAngle)-0.032077*sin(yearAngle)-0.014615*cos(2*yearAngle)-0.040849*sin(2*yearAngle))
        val declination=0.006918-0.399912*cos(yearAngle)+0.070257*sin(yearAngle)-0.006758*cos(2*yearAngle)+0.000907*sin(2*yearAngle)-0.002697*cos(3*yearAngle)+0.00148*sin(3*yearAngle)
        val minutes=((hour*60+equation+4*longitude)%1440+1440)%1440
        val hourAngle=Math.toRadians(minutes/4-180)
        val lat=Math.toRadians(latitude)
        val elevation=asin((sin(lat)*sin(declination)+cos(lat)*cos(declination)*cos(hourAngle)).coerceIn(-1.0,1.0))
        return elevation < Math.toRadians(-0.833)
    }
    fun styleId(dark: Boolean): String = if(dark) "streets-v4-dark" else "streets-v4"
}
