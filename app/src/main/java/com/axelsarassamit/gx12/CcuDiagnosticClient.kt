package com.axelsarassamit.gx12

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import app.pillion.core.CcuProtocol
import java.util.UUID
import java.util.ArrayDeque
import java.util.concurrent.Executors

/** User-started parked diagnostic session. Only observed authentication and data-read commands. */
@SuppressLint("MissingPermission")
class CcuDiagnosticClient(private val context: Context, private val report: (String) -> Unit,
                          private val received: (CcuProtocol.Message) -> Unit) {
    companion object {
        private val SERVICE = UUID.fromString("afa2cdf4-eccf-46a7-a5ea-9da428c0157a")
        private val CLASSIC = UUID.fromString("00008911-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val CONTROL = UUID.fromString("b606c7f9-e5a1-4e75-b313-2a920054a8eb")
        private val REPLY = UUID.fromString("9c810d26-b605-4306-8c1c-755a1ba3066c")
        private val STATUS = UUID.fromString("75ab2788-87f6-4f61-ba9c-6f85a12600d1")
        private val SEND_HEADER = UUID.fromString("56851826-a090-4f6a-a5df-d1776520f74a")
        private val SEND_DATA = UUID.fromString("e998aeab-ea8f-4063-83aa-2017c1a44484")
        private val RECEIVE_STATUS = UUID.fromString("1a1a7a79-3d72-42a1-b084-843058453068")
        private val RECEIVE_HEADER = UUID.fromString("77808451-e42f-4f6b-83ed-3ab44aa3d16a")
        private val RECEIVE_DATA = UUID.fromString("347456ee-a588-4dd5-bd98-62be3672e27a")
        private val VEHICLE = UUID.fromString("e974b9a1-fa31-43c6-91ae-09931f3f6ef1")
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var socket: BluetoothSocket? = null
    private var gatt: BluetoothGatt? = null
    private var service: BluetoothGattService? = null
    private var credentials = byteArrayOf()
    private var authenticated = false
    private var cacheName = ""
    private var supportedRequested = false
    private var supportedIds = emptyList<Int>()
    private var mtu = 23
    private var transferId = 0
    private var transfer = byteArrayOf()
    private var stage = ""
    private var lastRead = -10000L
    private val assembly = CcuProtocol.Assembly()
    private data class Operation(val kind: String, val uuid: UUID, val start: () -> Boolean, val done: () -> Unit)
    private val operations = ArrayDeque<Operation>()
    private var pending: Operation? = null
    private val operationTimeout = Runnable { fail("Bike did not finish a Bluetooth operation. Stop and reconnect while parked.") }
    private val transferTimeout = Runnable { fail("Bike data transfer timed out. No reading was accepted.") }
    private val setupTimeout = Runnable { fail("Bike connection timed out. Check ignition, selected CCU and close Y-Connect / nRF Connect.") }
    fun start(device: BluetoothDevice) {
        check(!closed && gatt == null && socket == null)
        report("Getting accessory connection details from the paired Yamaha…")
        main.postDelayed(setupTimeout, 30000)
        worker.execute {
            val result = runCatching {
                // A per-device encrypted cache is populated only by a response from the paired peer.
                cacheName = "ccu_" + device.address.replace(":", "")
                val cached = NavigationSecrets.read(context, cacheName)
                if (cached.isNotBlank()) {
                    val bytes = Base64.decode(cached, Base64.NO_WRAP)
                    CcuProtocol.authentication(bytes); bytes
                } else {
                    check(!closed)
                    val peer = device.createRfcommSocketToServiceRecord(CLASSIC)
                    socket = peer
                    try {
                        check(!closed); peer.connect(); check(!closed)
                        peer.outputStream.write(CcuProtocol.classic(byteArrayOf(0x01, 0x39))); peer.outputStream.flush()
                        var result: ByteArray? = null
                        repeat(16) {
                            if (result == null) {
                                val frame = CcuProtocol.readClassic(peer.inputStream)
                                if (CcuProtocol.u(frame, 0, 2) == 0x13a) result = CcuProtocol.credentials(frame)
                            }
                        }
                        val bytes = result ?: error("Connection details missing")
                        check(!closed)
                        NavigationSecrets.save(context, cacheName, Base64.encodeToString(bytes, Base64.NO_WRAP))
                        bytes
                    } finally { runCatching { peer.close() }; socket = null }
                }
            }
            main.post {
                if (closed) { result.getOrNull()?.fill(0); return@post }
                result.onSuccess { bytes ->
                    credentials = bytes
                    report("Connection details available. Connecting to the Yamaha data service…")
                    gatt = runCatching { device.connectGatt(context, false, callbacks, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
                    if (gatt == null) fail("Could not open the Yamaha data service.")
                }.onFailure { fail("Accessory connection details unavailable. Close Y-Connect / nRF Connect and check the paired Yamaha. Existing pairing is preserved.") }
            }
        }
    }
    private fun post(client: BluetoothGatt, block: () -> Unit) { main.post { if (!closed && client === gatt) block() } }
    private fun queue(kind: String, uuid: UUID, start: () -> Boolean, done: () -> Unit = {}) {
        if (closed) return
        check(operations.size < 128)
        operations.add(Operation(kind, uuid, start, done)); pump()
    }
    private fun pump() {
        if (closed || pending != null || operations.isEmpty()) return
        pending = operations.removeFirst()
        main.postDelayed(operationTimeout, 10000)
        if (!runCatching { pending!!.start() }.getOrDefault(false)) fail("Android could not start a bike Bluetooth operation.")
    }
    private fun finish(kind: String, uuid: UUID, status: Int, value: ByteArray? = null) {
        val active = pending ?: return
        if (active.kind != kind || active.uuid != uuid) return
        main.removeCallbacks(operationTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) { fail("Bike Bluetooth operation failed (status $status)."); return }
        pending = null
        if (value != null) notify(uuid, value)
        if (!closed) { active.done(); pump() }
    }
    @Suppress("DEPRECATION") private fun write(uuid: UUID, value: ByteArray, done: () -> Unit = {}) {
        val client = gatt ?: return
        val characteristic = service?.getCharacteristic(uuid) ?: run { fail("Expected Yamaha data characteristic missing."); return }
        queue("write", uuid, {
            if (Build.VERSION.SDK_INT >= 33) client.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else { characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; characteristic.value = value; client.writeCharacteristic(characteristic) }
        }, done)
    }
    private fun readStatus(done: () -> Unit = {}) {
        val client = gatt ?: return; val characteristic = service?.getCharacteristic(STATUS) ?: return
        queue("read", STATUS, { client.readCharacteristic(characteristic) }, done)
    }
    @Suppress("DEPRECATION") private fun subscribe(uuid: UUID) {
        val client = gatt ?: return
        val characteristic = service?.getCharacteristic(uuid) ?: run { fail("Expected Yamaha notification channel missing."); return }
        val descriptor = characteristic.getDescriptor(CCCD) ?: run { fail("Yamaha notification descriptor missing."); return }
        queue("descriptor", uuid, {
            client.setCharacteristicNotification(characteristic, true) &&
                if (Build.VERSION.SDK_INT >= 33) client.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; client.writeDescriptor(descriptor) }
        })
    }
    private fun begin(payload: ByteArray) {
        if (stage.isNotEmpty()) { report("A bike data request is still running."); return }
        transferId = transferId % 255 + 1
        transfer = payload.copyOf(); stage = "inactive"
        main.postDelayed(transferTimeout, 15000)
        readStatus()
    }
    fun readSnapshot() {
        if (!authenticated || closed) { report("Authenticate the Yamaha data connection first."); return }
        if (supportedIds.isEmpty()) { report("Waiting for the bike's supported-field reply. No unsupported fields will be requested."); return }
        val now = SystemClock.elapsedRealtime()
        if (now - lastRead < 5000 || stage.isNotEmpty()) { report("Wait for the current request before reading again."); return }
        lastRead = now
        // IDs and server observed on the owner's XMAX. This is a snapshot, not a settings or actuator write.
        report("Reading ${supportedIds.size} vehicle fields confirmed by the bike…")
        begin(CcuProtocol.vehicleRequest(1248, supportedIds))
    }
    private fun requestSupported() {
        if (!authenticated || stage.isNotEmpty() || supportedRequested) return
        supportedRequested = true
        // Fixed command plus BE16 server, verified in the reference SDK. No request ID is inserted.
        write(CONTROL, byteArrayOf(0x01, 0x26, 0x04, 0xe0.toByte()))
    }
    private fun notify(uuid: UUID, value: ByteArray) {
        if (closed) return
        runCatching {
            when (uuid) {
                STATUS -> {
                    if (value.size < 2) return
                    val inactive = value[0] == 0.toByte() && value[1] == 0.toByte()
                    when (stage) {
                        "inactive" -> {
                            check(inactive) { "Transfer busy" }; stage = "active"
                            write(SEND_HEADER, CcuProtocol.transferHeader(transferId, transfer.size)) { if (stage == "active") readStatus() }
                        }
                        "active" -> if (value[0] == 1.toByte() && value[1].toInt() and 255 == transferId) {
                            stage = "sending"
                            val width = (mtu - 7).coerceIn(16, 240)
                            for (offset in transfer.indices step width) {
                                val end = minOf(offset + width, transfer.size)
                                write(SEND_DATA, CcuProtocol.fragment(transferId, offset, transfer.copyOfRange(offset, end))) {
                                    if (end == transfer.size) { stage = "complete"; readStatus() }
                                }
                            }
                        }
                        "complete" -> if (inactive) {
                            transfer.fill(0); transfer = byteArrayOf(); stage = ""; main.removeCallbacks(transferTimeout)
                            requestSupported()
                        }
                    }
                }
                REPLY -> {
                    if (value.size == 3 && CcuProtocol.u(value, 0, 2) == 0x102) {
                        if (value[2] != 1.toByte()) {
                            runCatching { NavigationSecrets.save(context, cacheName, "") }
                            fail("Yamaha authentication was rejected. Reconnect to request fresh connection details; pairing is preserved."); return
                        }
                        authenticated = true; credentials.fill(0); main.removeCallbacks(setupTimeout)
                        report("Accessory authentication accepted. Waiting for vehicle packets. Use Read bike snapshot while parked.")
                        BikeDiagnostics.record(context, "CCU authentication accepted")
                        requestSupported()
                    }
                }
                RECEIVE_HEADER -> {
                    assembly.begin(value, SystemClock.elapsedRealtime())
                    write(RECEIVE_STATUS, byteArrayOf(1, value[0]))
                }
                RECEIVE_DATA -> {
                    assembly.accept(value, SystemClock.elapsedRealtime())?.let { frame ->
                        write(RECEIVE_DATA, byteArrayOf(value[0])) { write(RECEIVE_STATUS, byteArrayOf(0, 0)) }
                        decoded(CcuProtocol.message(frame))
                    }
                }
                VEHICLE -> {
                    require(value.size >= 9 && value[2] == 1.toByte() && value[3] == 1.toByte())
                    decoded(CcuProtocol.message(value.copyOfRange(4, value.size)))
                }
            }
        }.onFailure { fail("Unexpected or incomplete Yamaha data packet. Session stopped; no guessed reading was stored.") }
    }
    private fun decoded(message: CcuProtocol.Message) {
        if (message.command == 0x127) {
            supportedIds = CcuProtocol.supportedIds(message, 1248).intersect(setOf(1, 3, 9, 72)).sorted()
            report(if (supportedIds.isEmpty()) "Bike replied, but none of the observed XMAX fields were confirmed. No snapshot requested."
                else "Bike confirmed vehicle IDs ${supportedIds.joinToString()}. Tap Read bike snapshot.")
        }
        // Never store authentication, IDs or unknown replies in a general report.
        if (message.command in listOf(0x10f, 0x114, 0x127)) received(message)
    }
    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(client: BluetoothGatt, status: Int, state: Int) = post(client) {
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                if (!client.discoverServices()) fail("Yamaha service discovery could not start.")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS)
                fail("Bike data disconnected (status $status). Turn ignition on and reconnect while parked.")
        }
        override fun onServicesDiscovered(client: BluetoothGatt, status: Int) = post(client) {
            service = client.getService(SERVICE)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) { fail("This connection does not expose the observed XMAX accessory service."); return@post }
            queue("mtu", SERVICE, { client.requestMtu(247) }) {
                for (uuid in listOf(REPLY, STATUS, RECEIVE_HEADER, RECEIVE_DATA, VEHICLE)) subscribe(uuid)
                // Queue a status read behind all descriptor writes; authentication starts only after subscriptions finish.
                readStatus { if (!closed) { report("Authenticating the Yamaha accessory connection…"); begin(CcuProtocol.authentication(credentials)) } }
            }
        }
        override fun onMtuChanged(client: BluetoothGatt, value: Int, status: Int) = post(client) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = value.coerceIn(23, 247)
            // Negotiation refusal is harmless. Use the default MTU rather than aborting a valid connection.
            finish("mtu", SERVICE, BluetoothGatt.GATT_SUCCESS)
        }
        override fun onDescriptorWrite(client: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = post(client) {
            finish("descriptor", descriptor.characteristic.uuid, status)
        }
        override fun onCharacteristicWrite(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = post(client) {
            finish("write", characteristic.uuid, status)
        }
        override fun onCharacteristicRead(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) = post(client) {
            finish("read", characteristic.uuid, status, value.copyOf())
        }
        @Suppress("DEPRECATION") override fun onCharacteristicRead(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) { val copy = characteristic.value?.copyOf(); post(client) { finish("read", characteristic.uuid, status, copy) } }
        }
        override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf(); post(client) { notify(characteristic.uuid, copy) }
        }
        @Suppress("DEPRECATION") override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) { val copy = characteristic.value?.copyOf() ?: return; post(client) { notify(characteristic.uuid, copy) } }
        }
    }
    private fun fail(message: String) { if (!closed) { report(message); BikeDiagnostics.record(context, "CCU diagnostic session ended: $message"); close() } }
    fun close() {
        closed = true; main.removeCallbacksAndMessages(null); authenticated = false
        credentials.fill(0); transfer.fill(0); operations.clear(); pending = null; assembly.clear()
        runCatching { socket?.close() }; socket = null
        runCatching { gatt?.disconnect(); gatt?.close() }; gatt = null; service = null
        worker.shutdownNow()
    }
}
