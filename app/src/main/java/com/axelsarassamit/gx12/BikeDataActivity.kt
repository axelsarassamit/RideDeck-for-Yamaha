package com.axelsarassamit.gx12

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import app.pillion.core.CcuProtocol
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Parked connection verification. Raw bytes never become invented fuel or brake readings. */
class BikeDataActivity : RiderPage() {
    companion object { @JvmStatic fun open(a: Activity) { a.startActivity(Intent(a, BikeDataActivity::class.java)) } }
    private var client: CcuDiagnosticClient? = null
    private lateinit var readings: android.widget.TextView
    private lateinit var history: android.widget.TextView
    private var count = 0L
    private var rejected = 0L
    private var lastPacket = 0L
    private val data = linkedMapOf<String, Pair<ByteArray, Long>>()
    private val timer = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopAfterTenMinutes = Runnable { stop("Parked test finished after 10 minutes. Reconnect to start another session.") }
    private var exporting: File? = null
    private val log by lazy { File(filesDir, "ccu-parked-test.jsonl") }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved); build("Bike data and fuel range")
        state.text = "Parked test mode. Close Y-Connect and nRF Connect before starting an independent RideDeck connection. Keep the existing bike pairing."
        row(button("Connect to parked Yamaha") { choose() })
        row(button("Read bike snapshot") { client?.readSnapshot() ?: run { state.text = "Connect to the parked Yamaha first." } })
        row(button("Stop bike-data test") { stop("Bike-data test stopped. Navigation uses its separate connection.") })
        history = text("No vehicle packets received yet."); row(history)
        readings = text("Fuel level, consumption and range: waiting for verified bike fields and units.\nBrake and indicator states: not decoded yet."); row(readings)
        row(button("Mark a test action") {
            android.app.AlertDialog.Builder(this).setTitle("Record the state you can see").setItems(arrayOf(
                "Baseline: brakes / indicators off", "Front brake held", "Front brake released", "Rear brake held", "Rear brake released",
                "Left indicator on", "Left indicator off", "Right indicator on", "Right indicator off", "Hazards on", "Hazards off"
            )) { _, i ->
                val labels = listOf("baseline", "front_on", "front_off", "rear_on", "rear_off", "left_on", "left_off", "right_on", "right_off", "hazards_on", "hazards_off")
                if (client == null) state.text = "Start a bike-data session before marking an action."
                else { append(JSONObject().put("event", labels[i])); state.text = "Test action recorded. This label describes your observation; it is not a decoded bike reading." }
            }.setNegativeButton("Cancel", null).show()
        })
        row(button("Save private test log") {
            stop("Bike-data test stopped so the private log can be saved.")
            val snapshot = File(cacheDir, "ccu-export.jsonl")
            snapshot.outputStream().use { out ->
                for (file in listOf(File(filesDir, "ccu-parked-test.previous.jsonl"), log)) if (file.exists()) file.inputStream().use { it.copyTo(out) }
            }
            exporting = snapshot
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "yamaha-private-test-${System.currentTimeMillis()}.jsonl"), 99)
        })
        row(text("Raw vehicle fields are shown by ID with receive times. Counters and byte changes do not prove a brake or indicator signal. No pairing codes, account details or authentication packets are written to this test log. Keep the log private."))
        row(button("Find a fuel station") { FuelStationsActivity.open(this) })
    }
    private fun choose() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 98); state.text = "Allow nearby devices, then connect again."; return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        val devices = runCatching { adapter?.bondedDevices?.filter { it.name.orEmpty().contains("YCCU", true) || it.name.orEmpty().contains("Yamaha", true) }.orEmpty() }.getOrDefault(emptyList())
        if (devices.isEmpty()) { state.text = "No paired Yamaha CCU found. Turn Bluetooth on and check the existing Yamaha pairing in Android."; return }
        android.app.AlertDialog.Builder(this).setTitle("Select your parked Yamaha CCU")
            .setItems(devices.map { it.name ?: "Yamaha CCU" }.toTypedArray()) { _, i -> connect(devices[i]) }.setNegativeButton("Cancel", null).show()
    }
    private fun connect(device: BluetoothDevice) {
        stop(""); count = 0; rejected = 0; lastPacket = 0; data.clear(); showData()
        append(JSONObject().put("event", "session_start").put("decoder", "raw-ccu-v1").put("version", BuildConfig.VERSION_NAME))
        client = CcuDiagnosticClient(applicationContext, { state.text = it }, ::received)
        client!!.start(device); timer.postDelayed(stopAfterTenMinutes, 600000); timer.post(tick)
    }
    private fun received(message: CcuProtocol.Message) {
        val decoded = runCatching { CcuProtocol.readings(message) }
        if (decoded.isFailure) { rejected++; return }
        count++; lastPacket = SystemClock.elapsedRealtime()
        val fields = decoded.getOrThrow()
        val category = if (message.command == 0x114) "CAN" else "Vehicle"
        for (reading in fields) {
            data["$category ID ${reading.id}"] = reading.bytes.copyOf() to System.currentTimeMillis()
            if (data.size > 256) data.remove(data.keys.first())
        }
        append(JSONObject().put("command", message.command).put("fields", JSONArray().apply {
            fields.forEach { put(JSONObject().put("id", it.id).put("raw", hex(it.bytes))) }
        }))
        showData()
    }
    private val tick = object : Runnable {
        override fun run() { showData(); if (client != null) timer.postDelayed(this, 1000) }
    }
    private fun showData() {
        history.text = "$count vehicle packets received · $rejected malformed packets excluded\n" +
            if (lastPacket == 0L) "Waiting for vehicle data." else "Last packet ${(SystemClock.elapsedRealtime() - lastPacket) / 1000} seconds ago."
        readings.text = "Fuel range: waiting for confirmed remaining fuel and consumption.\nBrake / indicator states: not decoded yet.\n\n" + data.entries.take(16).joinToString("\n\n") {
            "${it.key}\nRaw ${hex(it.value.first).take(120)}\nReceived ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it.value.second))}"
        }
    }
    private fun append(value: JSONObject) {
        runCatching {
            if (log.length() > 2_000_000) { val old = File(filesDir, "ccu-parked-test.previous.jsonl"); old.delete(); log.renameTo(old) }
            log.appendText(value.put("received_ms", System.currentTimeMillis()).put("elapsed_ms", SystemClock.elapsedRealtime()).toString() + "\n", Charsets.UTF_8)
        }.onFailure { BikeDiagnostics.record(this, "CCU private log unavailable ${it.javaClass.simpleName}") }
    }
    private fun stop(message: String) {
        timer.removeCallbacksAndMessages(null); client?.close(); client = null
        if (message.isNotBlank()) { state.text = message; append(JSONObject().put("event", "session_stop")) }
    }
    @Deprecated("Android activity result compatibility") override fun onActivityResult(request: Int, result: Int, intent: Intent?) {
        super.onActivityResult(request, result, intent)
        if (request == 99) {
            val file = exporting; exporting = null
            val success = result == RESULT_OK && file != null && intent?.data != null && runCatching {
                val out = contentResolver.openOutputStream(intent.data!!) ?: error("No destination")
                out.use { file.inputStream().use { source -> source.copyTo(it) } }
            }.isSuccess
            state.text = if (success) "Private test log saved. Share it only with someone you trust." else "Log was not exported. The local test data is still on this phone."
            file?.delete()
        }
    }
    override fun onStop() { stop("Bike-data test stopped because its screen closed."); super.onStop() }
    override fun onDestroy() { stop(""); super.onDestroy() }
}
