package com.axelsarassamit.gx12
import android.content.Context
import android.hardware.*
import android.location.*
import app.pillion.core.RideMeasurements

/** Phone measurements only. No unverified Yamaha vehicle commands. */
class RideTelemetry(private val c:Context):SensorEventListener,LocationListener {
    private val manager=c.getSystemService(SensorManager::class.java)
    private val locations=c.getSystemService(LocationManager::class.java)
    private var up:DoubleArray?=null
    private var sensorAt=0L
    private var stableLean:Double?=null
    private var previousLean:Double?=null
    private var stableCount=0
    fun sensors():Boolean {
        val sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return false
        return manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)
    }
    fun start() {
        sensors()
        runCatching { locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0f,this,android.os.Looper.getMainLooper()) }
    }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) { stableLean=null;up=null;return }
        val matrix=FloatArray(9);SensorManager.getRotationMatrixFromVector(matrix,event.values)
        up=doubleArrayOf(matrix[6].toDouble(),matrix[7].toDouble(),matrix[8].toDouble());sensorAt=event.timestamp
        val baseline=RidePreferences.prefs(c).getString("lean_baseline","").orEmpty().split(',').mapNotNull { it.toDoubleOrNull() }.toDoubleArray()
        val angle=RideMeasurements.lean(up!!,baseline)
        if(angle!=null && previousLean?.let { kotlin.math.abs(angle-it)<=3 }==true) stableCount++ else stableCount=0
        previousLean=angle;stableLean=angle?.takeIf { stableCount>=5 }
    }
    fun calibrate():Boolean {
        val value=up ?: return false
        if(android.os.SystemClock.elapsedRealtimeNanos()-sensorAt !in 0..1_000_000_000L || RideMeasurements.lean(value,value)==null) return false
        RidePreferences.prefs(c).edit().putString("lean_baseline",value.joinToString(",")).apply();return true
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) { if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) stableLean=null }
    override fun onLocationChanged(location:Location) {
        val lean=stableLean?.takeIf { android.os.SystemClock.elapsedRealtimeNanos()-sensorAt in 0..1_000_000_000L }
        RideBookStore.record(c,location,lean)
    }
    fun stop() { manager.unregisterListener(this);runCatching { locations.removeUpdates(this) } }
}
