package app.pillion.core

import java.io.InputStream
import java.io.EOFException
import java.io.ByteArrayOutputStream

/** Independent, bounded interoperability codec. It does not assign physical units to raw fields. */
object CcuProtocol {
    const val MAX_MESSAGE = 65536
    data class Message(val command: Int, val payload: ByteArray)
    data class Field(val number: Int, val integer: Long? = null, val bytes: ByteArray? = null)
    data class Reading(val id: Int, val bytes: ByteArray)
    fun u(bytes: ByteArray, at: Int, size: Int): Int {
        require(size in 1..4 && at >= 0 && at + size <= bytes.size)
        var value = 0L
        repeat(size) { value = (value shl 8) or (bytes[at + it].toLong() and 255) }
        require(value <= Int.MAX_VALUE)
        return value.toInt()
    }
    private fun be(value: Int, size: Int) = ByteArray(size) { (value ushr ((size - 1 - it) * 8)).toByte() }
    fun classic(payload: ByteArray): ByteArray {
        require(payload.size in 2..MAX_MESSAGE)
        return byteArrayOf(1) + be(payload.size, 4) + payload + byteArrayOf(4)
    }
    fun readClassic(input: InputStream): ByteArray {
        // Bound resynchronisation, even on an unrelated or corrupt peer.
        var found = false
        repeat(256) { if (!found) { val b = input.read(); if (b < 0) throw EOFException(); found = b == 1 } }
        require(found) { "Classic start missing" }
        fun read(size: Int): ByteArray {
            val out = ByteArray(size); var at = 0
            while (at < size) { val count = input.read(out, at, size - at); if (count <= 0) throw EOFException(); at += count }
            return out
        }
        val length = u(read(4), 0, 4)
        require(length in 2..MAX_MESSAGE) { "Classic length invalid" }
        val payload = read(length)
        require(input.read() == 4) { "Classic terminator invalid" }
        return payload
    }
    fun credentials(response: ByteArray): ByteArray {
        require(response.size == 22 && u(response, 0, 2) == 0x13a)
        require(response.takeLast(6).all { it.toInt() in 48..57 }) { "Unexpected connection credential format" }
        return response.copyOfRange(2, 22)
    }
    fun authentication(credentials: ByteArray): ByteArray {
        require(credentials.size == 20 && credentials.takeLast(6).all { it.toInt() in 48..57 })
        return be(0x101, 2) + credentials
    }
    fun envelope(command: Int, payload: ByteArray) = be(command, 2) + be(payload.size, 3) + payload
    fun message(value: ByteArray): Message {
        require(value.size in 5..MAX_MESSAGE)
        require(u(value, 2, 3) == value.size - 5) { "CCU payload length invalid" }
        return Message(u(value, 0, 2), value.copyOfRange(5, value.size))
    }
    fun transferHeader(id: Int, size: Int): ByteArray {
        require(id in 1..255 && size in 2..MAX_MESSAGE)
        return byteArrayOf(id.toByte()) + be(size, 3)
    }
    fun fragment(id: Int, offset: Int, data: ByteArray): ByteArray {
        require(id in 1..255 && offset in 0..MAX_MESSAGE && data.isNotEmpty() && offset + data.size <= MAX_MESSAGE)
        return byteArrayOf(id.toByte()) + be(offset, 3) + data
    }
    private fun varint(out: ByteArrayOutputStream, value: Long) {
        require(value >= 0); var left = value
        while (left >= 128) { out.write((left.toInt() and 127) or 128); left = left ushr 7 }
        out.write(left.toInt())
    }
    fun integer(field: Int, value: Long): ByteArray = ByteArrayOutputStream().apply {
        require(field in 1..536870911); varint(this, field.toLong() shl 3); varint(this, value)
    }.toByteArray()
    fun vehicleRequest(server: Int, ids: List<Int>): ByteArray {
        require(server in 0..65535 && ids.size in 1..256 && ids.all { it in 0..65535 })
        return envelope(0x10e, integer(3, server.toLong()) + ids.distinct().fold(byteArrayOf()) { a, id -> a + integer(4, id.toLong()) })
    }
    fun fields(bytes: ByteArray): List<Field> {
        require(bytes.size <= MAX_MESSAGE); var at = 0
        fun variable(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(at < bytes.size) { "Truncated varint" }
                val b = bytes[at++].toInt() and 255
                require(shift != 63 || b <= 1) { "Oversized varint" }
                value = value or ((b and 127).toLong() shl shift)
                if (b < 128) return value
            }
            error("Oversized varint")
        }
        val out = mutableListOf<Field>()
        while (at < bytes.size) {
            require(out.size < 4096)
            val tag = variable(); require(tag in 8..4294967295L)
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> out.add(Field(field, integer = variable()))
                1, 2, 5 -> {
                    val size = when ((tag and 7).toInt()) { 1 -> 8L; 5 -> 4L; else -> variable() }
                    require(size in 0..(bytes.size - at).toLong()) { "Truncated field" }
                    out.add(Field(field, bytes = bytes.copyOfRange(at, at + size.toInt()))); at += size.toInt()
                }
                else -> error("Unsupported wire type")
            }
        }
        return out
    }
    fun readings(message: Message): List<Reading> {
        val list = when (message.command) { 0x10f -> 4; 0x114 -> 3; else -> return emptyList() }
        return fields(message.payload).filter { it.number == list && it.bytes != null }.mapNotNull { item ->
            val nested = fields(item.bytes!!)
            val id = nested.firstOrNull { it.number == 1 }?.integer
            val raw = nested.firstOrNull { it.number == 2 }?.bytes
            if (id == null || id !in 0..65535 || raw == null || raw.size > 4096) null else Reading(id.toInt(), raw)
        }
    }
    fun supportedIds(message: Message, server: Int): List<Int> {
        require(message.command == 0x127)
        val decoded = fields(message.payload)
        require(decoded.firstOrNull { it.number == 3 }?.integer == server.toLong()) { "Unexpected vehicle server" }
        val ids = mutableListOf<Int>()
        for (field in decoded.filter { it.number == 4 }) {
            field.integer?.let { require(it in 0..65535); ids.add(it.toInt()) }
            field.bytes?.let { packed ->
                var at = 0
                while (at < packed.size) {
                    var value = 0; var complete = false
                    for (shift in 0..14 step 7) {
                        require(at < packed.size); val b = packed[at++].toInt() and 255
                        value = value or ((b and 127) shl shift)
                        if (b < 128) { complete = true; break }
                    }
                    require(complete && value in 0..65535); ids.add(value)
                }
            }
            require(ids.size <= 256)
        }
        return ids.distinct()
    }
    /** Ordered transfer with exact duplicates accepted. Missing, conflicting and oversized fragments fail closed. */
    class Assembly {
        private var id = -1; private var expected = 0; private var at = 0; private var started = 0L
        private var data = byteArrayOf()
        fun clear() { id = -1; data = byteArrayOf(); at = 0 }
        fun begin(header: ByteArray, now: Long) {
            clear(); require(header.size == 4)
            val size = u(header, 1, 3); require(size in 2..MAX_MESSAGE)
            id = header[0].toInt() and 255; expected = size; data = ByteArray(size); started = now
        }
        fun accept(fragment: ByteArray, now: Long): ByteArray? {
            require(id >= 0 && now - started in 0..15000 && fragment.size > 4)
            require(fragment[0].toInt() and 255 == id)
            val offset = u(fragment, 1, 3); val part = fragment.copyOfRange(4, fragment.size)
            require(offset <= at && offset + part.size <= expected)
            if (offset < at) { require(offset + part.size <= at && data.copyOfRange(offset, offset + part.size).contentEquals(part)); return null }
            part.copyInto(data, offset); at += part.size
            if (at != expected) return null
            val completed = data.copyOf(); clear(); return completed
        }
    }
}
