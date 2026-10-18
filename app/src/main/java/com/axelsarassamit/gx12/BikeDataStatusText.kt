package com.axelsarassamit.gx12

import app.pillion.core.CcuAdditionalFields
import app.pillion.protocol.YamahaStatusUpdates.Kind

/** Diagnostics only: a completed Bluetooth write is not a verified dashboard graphic. */
object BikeDataStatusText {
    fun format(s: BikeDataState, now: Long): String =
        "Additional bike fields\n" + if (s.additionalFields.isEmpty()) {
            "Not checked this session. Park first, then use Check additional bike fields.\n\n" + phoneWrites(s, now)
        } else {
            s.additionalFields.joinToString("\n") { item ->
                "ID ${item.id}: " + when (item.result) {
                    CcuAdditionalFields.Result.NOT_ADVERTISED -> "not declared by this bike"
                    CcuAdditionalFields.Result.WAITING -> "waiting for a reply"
                    CcuAdditionalFields.Result.REPLIED -> "reply received, ${item.bytes} bytes; meaning not verified"
                    CcuAdditionalFields.Result.TIMED_OUT -> "no matching reply within 10 seconds"
                    CcuAdditionalFields.Result.CANCELLED -> "check cancelled"
                }
            } + "\nUnknown payloads are not saved.\n\n" + phoneWrites(s, now)
        }

    fun phoneWrites(s: BikeDataState, now: Long): String {
        fun yesNo(value: Boolean?) = when (value) { true -> "Yes"; false -> "No"; null -> "Unavailable" }
        fun line(kind: Kind, label: String, detail: (PhoneStatusWrite) -> String): String {
            val write = s.phoneWrites[kind] ?: return "$label: not sent this session"
            return "$label: ${detail(write)} · ${write.count} writes · ${((now-write.at)/1000).coerceAtLeast(0)} s ago"
        }
        return "Phone status sent to bike${if(s.active && s.authenticated) "" else " (last session)"}\n" +
            line(Kind.BATTERY,"Battery") { "${it.battery}% · charging ${yesNo(it.charging)}" } + "\n" +
            line(Kind.SIGNAL,"Cellular") { when(it.signal) { 0 -> "No connection"; 1 -> "No signal"; else -> "${(it.signal ?: 1)-1} of 4 bars" } } + "\n" +
            line(Kind.HEADSET,"Headset") { yesNo(it.headset) } + "\n" +
            line(Kind.COMBINED,"Combined phone status") { "${it.battery}% · charging ${yesNo(it.charging)} · headset ${yesNo(it.headset)}" } +
            "\nWrites confirm Bluetooth delivery, not the bike's icon appearance."
    }
}
