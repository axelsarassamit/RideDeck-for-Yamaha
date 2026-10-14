package com.axelsarassamit.gx12
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import java.util.concurrent.Executors
import app.pillion.core.RideFix
import app.pillion.core.RideMeasurements
import app.pillion.core.BikeValue
import app.pillion.core.CcuProtocol

data class BikePeak(val minimum:Double,val minimumAt:Long,val maximum:Double,val maximumAt:Long,val unit:String,val source:String)
data class RideRecord(val id:Long,val started:Long,val state:String,val seconds:Long,val meters:Double,
    val peaks:Map<String,Pair<Double,Long>>,val bike:Map<String,BikePeak> = emptyMap())
object RideBookStore {
    private val worker=Executors.newSingleThreadExecutor()
    private var helper:Database?=null
    private var id=0L
    private var running=false
    private var previous:Location?=null
    private var previousFix:RideFix?=null
    private var periodStarted=0L
    private var lastSaved=0L
    private val peakColumns=listOf("speed","accel","decel","lean")
    private class Database(c:Context):SQLiteOpenHelper(c,"yamaha-rides.db",null,2) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER NOT NULL, state TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, meters REAL NOT NULL DEFAULT 0, speed REAL, speed_at INTEGER, accel REAL, accel_at INTEGER, decel REAL, decel_at INTEGER, lean REAL, lean_at INTEGER)")
            db.execSQL("CREATE TABLE samples (ride INTEGER NOT NULL, time INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy REAL NOT NULL, lean REAL)")
            db.execSQL("CREATE INDEX sample_ride ON samples(ride,time)")
            bikeSchema(db)
        }
        private fun bikeSchema(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS bike_samples (ride INTEGER NOT NULL, time INTEGER NOT NULL, elapsed INTEGER NOT NULL, metric TEXT NOT NULL, value REAL NOT NULL, unit TEXT NOT NULL, source TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS bike_sample_ride ON bike_samples(ride,time)")
            db.execSQL("CREATE TABLE IF NOT EXISTS bike_peaks (ride INTEGER NOT NULL, metric TEXT NOT NULL, minimum REAL NOT NULL, minimum_at INTEGER NOT NULL, maximum REAL NOT NULL, maximum_at INTEGER NOT NULL, unit TEXT NOT NULL, source TEXT NOT NULL, PRIMARY KEY(ride,metric))")
            db.execSQL("CREATE TABLE IF NOT EXISTS bike_raw_samples (ride INTEGER NOT NULL, time INTEGER NOT NULL, elapsed INTEGER NOT NULL, category TEXT NOT NULL, field_id INTEGER NOT NULL, raw BLOB NOT NULL, decoder TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS bike_raw_ride ON bike_raw_samples(ride,time)")
            db.execSQL("CREATE TABLE IF NOT EXISTS bike_events (ride INTEGER NOT NULL, time INTEGER NOT NULL, elapsed INTEGER NOT NULL, event TEXT NOT NULL, source TEXT NOT NULL)")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) { if(old<2) bikeSchema(db) }
    }
    private fun db(c:Context):SQLiteDatabase {
        if(helper==null) {
            helper=Database(c.applicationContext)
            helper!!.writableDatabase.execSQL("UPDATE rides SET state='Paused' WHERE state='Recording'")
        }
        return helper!!.writableDatabase
    }
    fun start(c:Context) { worker.execute {
        val d=db(c);if(running) return@execute
        if(id==0L) d.rawQuery("SELECT id FROM rides WHERE state='Paused' ORDER BY id DESC LIMIT 1",null).use { if(it.moveToFirst()) id=it.getLong(0) }
        if(id==0L) id=d.insertOrThrow("rides",null,ContentValues().apply { put("started",System.currentTimeMillis());put("state","Recording") })
        d.execSQL("UPDATE rides SET state='Recording' WHERE id=?",arrayOf<Any>(id));running=true
        previous=null;previousFix=null;periodStarted=android.os.SystemClock.elapsedRealtime();lastSaved=0
    } }
    private fun duration(d:SQLiteDatabase) {
        if(!running) return
        val now=android.os.SystemClock.elapsedRealtime();val delta=(now-periodStarted)/1000
        if(delta>0) { d.execSQL("UPDATE rides SET seconds=seconds+? WHERE id=?",arrayOf<Any>(delta,id));periodStarted+=delta*1000 }
    }
    fun pause(c:Context,end:Boolean=false) { worker.execute {
        val d=db(c);duration(d);running=false;previous=null;previousFix=null
        if(id==0L && end) d.rawQuery("SELECT id FROM rides WHERE state='Paused' ORDER BY id DESC LIMIT 1",null).use { if(it.moveToFirst()) id=it.getLong(0) }
        if(id!=0L) d.execSQL("UPDATE rides SET state=? WHERE id=?",arrayOf<Any>(if(end) "Finished" else "Paused",id))
        if(end) id=0L
    } }
    fun record(c:Context,location:Location,lean:Double?) { val copy=Location(location);worker.execute {
        if(!running) return@execute
        val now=android.os.SystemClock.elapsedRealtimeNanos()
        val f=RideFix(copy.elapsedRealtimeNanos,copy.speed.toDouble(),copy.accuracy.toDouble(),if(copy.hasSpeedAccuracy()) copy.speedAccuracyMetersPerSecond.toDouble() else Double.NaN)
        if(!copy.hasSpeed() || !copy.hasAccuracy() || !RideMeasurements.trusted(f,now)) { previous=null;previousFix=null;return@execute }
        if(previousFix?.nanos?.let { f.nanos<=it }==true) return@execute
        if(!RideMeasurements.plausible(previousFix,f)) return@execute
        val d=db(c);duration(d)
        fun peak(column:String,value:Double) {
            if(!value.isFinite() || value<0) return
            d.execSQL("UPDATE rides SET $column=?, ${column}_at=? WHERE id=? AND ($column IS NULL OR $column<?)",arrayOf<Any>(value,copy.time,id,value))
        }
        peak("speed",f.speed*3.6)
        RideMeasurements.acceleration(previousFix,f)?.let { peak(if(it>=0) "accel" else "decel",kotlin.math.abs(it)) }
        previous?.let { old->
            val seconds=(copy.elapsedRealtimeNanos-old.elapsedRealtimeNanos)/1e9
            val distance=old.distanceTo(copy).toDouble()
            if(seconds in 1.0..5.0 && f.speed>=1.0 && distance<=70*seconds && distance>=2 && distance.isFinite())
                d.execSQL("UPDATE rides SET meters=meters+? WHERE id=?",arrayOf<Any>(distance,id))
        }
        val acceptedLean=lean?.takeIf { f.speed>=3 && kotlin.math.abs(it)<=75 }
        acceptedLean?.let { peak("lean",kotlin.math.abs(it)) }
        if(f.nanos-lastSaved>=1_000_000_000L) {
            d.insertOrThrow("samples",null,ContentValues().apply {
                put("ride",id);put("time",copy.time);put("lat",copy.latitude);put("lon",copy.longitude);put("speed",f.speed);put("accuracy",f.accuracy)
                acceptedLean?.let { put("lean",it) }
            });lastSaved=f.nanos
        }
        previous=copy;previousFix=f
    } }
    fun records(c:Context,answer:(List<RideRecord>)->Unit) { worker.execute {
        val d=db(c);duration(d);val records=mutableListOf<RideRecord>()
        d.rawQuery("SELECT * FROM rides ORDER BY id DESC",null).use { q->while(q.moveToNext()) {
            fun number(name:String)=q.getColumnIndexOrThrow(name)
            val peaks=peakColumns.mapNotNull { column->if(q.isNull(number(column))) null else column to (q.getDouble(number(column)) to q.getLong(number(column+"_at"))) }.toMap()
            val ride=q.getLong(number("id"));val bike=linkedMapOf<String,BikePeak>()
            d.rawQuery("SELECT * FROM bike_peaks WHERE ride=?",arrayOf(ride.toString())).use { b->while(b.moveToNext()) {
                fun col(name:String)=b.getColumnIndexOrThrow(name)
                bike[b.getString(col("metric"))]=BikePeak(b.getDouble(col("minimum")),b.getLong(col("minimum_at")),b.getDouble(col("maximum")),b.getLong(col("maximum_at")),b.getString(col("unit")),b.getString(col("source")))
            } }
            records.add(RideRecord(ride,q.getLong(number("started")),q.getString(number("state")),q.getLong(number("seconds")),q.getDouble(number("meters")),peaks,bike))
        } }
        android.os.Handler(android.os.Looper.getMainLooper()).post { answer(records) }
    } }
    fun recordBike(c:Context,values:List<BikeValue>,category:String,raw:List<CcuProtocol.Reading>,wall:Long,elapsed:Long) {
        val copied=raw.map { CcuProtocol.Reading(it.id,it.bytes.copyOf()) }
        worker.execute {
            if(!running || id==0L) return@execute
            val d=db(c);duration(d);d.beginTransaction()
            try {
                for(value in values) {
                    if(!value.value.isFinite() || !value.fresh(elapsed)) continue
                    d.insertOrThrow("bike_samples",null,ContentValues().apply { put("ride",id);put("time",value.wallMs);put("elapsed",value.elapsedMs);put("metric",value.metric.name);put("value",value.value);put("unit",value.metric.unit);put("source",value.source) })
                    d.execSQL("INSERT OR IGNORE INTO bike_peaks(ride,metric,minimum,minimum_at,maximum,maximum_at,unit,source) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(id,value.metric.name,value.value,value.wallMs,value.value,value.wallMs,value.metric.unit,value.source))
                    d.execSQL("UPDATE bike_peaks SET minimum=?,minimum_at=? WHERE ride=? AND metric=? AND minimum>?",arrayOf<Any>(value.value,value.wallMs,id,value.metric.name,value.value))
                    d.execSQL("UPDATE bike_peaks SET maximum=?,maximum_at=? WHERE ride=? AND metric=? AND maximum<?",arrayOf<Any>(value.value,value.wallMs,id,value.metric.name,value.value))
                }
                for(item in copied) if(item.bytes.size<=4096) d.insertOrThrow("bike_raw_samples",null,ContentValues().apply {
                    put("ride",id);put("time",wall);put("elapsed",elapsed);put("category",category);put("field_id",item.id);put("raw",item.bytes);put("decoder","raw-ccu-v1")
                })
                d.setTransactionSuccessful()
            } finally { d.endTransaction() }
        }
    }
    fun markBike(c:Context,event:String,wall:Long,elapsed:Long) { worker.execute {
        if(!running || id==0L) return@execute
        db(c).insertOrThrow("bike_events",null,ContentValues().apply { put("ride",id);put("time",wall);put("elapsed",elapsed);put("event",event);put("source","user_observation") })
    } }
}
