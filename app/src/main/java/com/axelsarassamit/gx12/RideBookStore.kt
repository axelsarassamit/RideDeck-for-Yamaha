package com.axelsarassamit.gx12
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import java.util.concurrent.Executors
import app.pillion.core.RideFix
import app.pillion.core.RideMeasurements

data class RideRecord(val id:Long,val started:Long,val state:String,val seconds:Long,val meters:Double,
    val peaks:Map<String,Pair<Double,Long>>)
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
    private class Database(c:Context):SQLiteOpenHelper(c,"yamaha-rides.db",null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER NOT NULL, state TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, meters REAL NOT NULL DEFAULT 0, speed REAL, speed_at INTEGER, accel REAL, accel_at INTEGER, decel REAL, decel_at INTEGER, lean REAL, lean_at INTEGER)")
            db.execSQL("CREATE TABLE samples (ride INTEGER NOT NULL, time INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy REAL NOT NULL, lean REAL)")
            db.execSQL("CREATE INDEX sample_ride ON samples(ride,time)")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) { }
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
            records.add(RideRecord(q.getLong(number("id")),q.getLong(number("started")),q.getString(number("state")),q.getLong(number("seconds")),q.getDouble(number("meters")),peaks))
        } }
        android.os.Handler(android.os.Looper.getMainLooper()).post { answer(records) }
    } }
}
