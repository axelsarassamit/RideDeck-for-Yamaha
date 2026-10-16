# Yamaha connection test 0.12.47

0.12.46 established an authenticated bike-data session but disconnected with status 19 while ignition was still on. The owner confirmed it happened before stopping the session or turning ignition off.

An offline comparison found a missing liveness exchange. In the latest official Y-Connect capture window, all eleven fixed-format keepalive requests received matching replies within approximately 4 to 22 milliseconds. In the RideDeck session, three requests arrived approximately ten seconds apart and received no replies. Disconnection followed approximately ten seconds after the last request.

This build handles the exact three-byte keepalive request and echoes its one-byte challenge in the observed three-byte response. The reply uses the existing control characteristic and remains serialized with other GATT operations, ahead of queued bulk-data work. It does not advance the startup read plan, appear as telemetry, alter credentials, reset pairing, or trigger automatic reconnects. Malformed requests do not produce replies. The same response framing is corroborated by the local reference SDK's parser and serializer.

The official comparison was deliberately ended by switching ignition off. Its disconnect does not prove a normal burst-sync policy. Status 19 must remain a disconnect, not be silently relabeled as successful synchronization.

## Verification

Unit checks cover every possible challenge byte, strict frame length, unrelated commands, preservation of the incoming bytes, and startup reads interleaved with keepalive traffic. Physical stability still requires a parked test.

For that test, connect with ignition on and engine off, keep Y-Connect/nRF Connect disconnected, and observe for five minutes without pressing Stop. Record successful keepalive replies, data freshness and any disconnect. Compare displayed readings with the dashboard or a separate reference before claiming their physical accuracy. Stop data before turning ignition off, and preserve the private diagnostic log. No engine-running step is needed.

Raw packet captures, request challenges, authentication values, decompiled source and device identifiers are excluded from this document and the release.
