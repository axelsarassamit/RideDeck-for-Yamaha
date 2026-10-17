# Yamaha 0.12.49: automatic connection and focused data checks

This is a test build. Indoor tests do not establish bike behavior. Original package and signing identity are preserved.

## Automatic connection

Bike data and the map use the same selected paired CCU, over their separate existing channels. `Auto-connect bike data with the map` is enabled by default and can be disabled in Bike data and fuel range. The existing map auto-connect setting still controls map startup.

After a successful navigation handshake and supported display-size check, wait three seconds before requesting the accessory/data connection. Nearby-device permissions and the same exact paired address are required. An already-active data connection is not replaced. No independent boot scan, rebonding or pairing reset is added.

Data startup has at most three attempts per successful map connection, spaced at least thirty seconds after a terminated data attempt. Stop bike data pauses new automatic starts until the next map connection. Turning the option off cancels pending starts and stops only data started automatically. Losing/stopping the map ends automatically started data; a separately started manual data session remains independent. Map sharing is not stopped by data failure. These interactions require physical testing.

## Additional field check

With bike parked, ignition on and engine off, connect and wait for normal setup to finish. Tap `Check additional bike fields` and confirm `Start check`.

- Reads only IDs 3 and 9 if this bike advertised them in its supported list.
- Uses the existing vehicle-read command 270, one ID per request. Does not alter telemetry subscriptions.
- Each reply must match command 271, server 1248 and the requested ID. Ordinary live engine data or an unrelated reply cannot complete the check.
- Each ID has a ten-second response deadline. Missing replies do not disconnect an otherwise live session or trigger retries.
- Leave thirty seconds between check runs. Stop cancels the check.
- Results distinguish not advertised, waiting, replied with byte count, timed out and cancelled. Reply presence is not proof of a usable decoded feature.
- Unknown ID 3/9 payloads are excluded from raw logs, raw UI and ride book. Only ID, outcome and byte length are retained. A future payload investigation needs a separate positive schema/privacy review.

## Phone status and charging

The diagnostics now show the most recent successful battery/charging, cellular and headset writes, their counts and ages. These counters update after successful Bluetooth writes, not merely after reading Android state. They do not prove native dashboard rendering. The phone-status preview excludes the selected data/map peer from headset detection.

October 17 captures independently show charging flag 1, then 0 during USB removal, then 1 after reconnection. The owner reported no visual battery-symbol change. The encoder was rechecked against the source serializer and remains unchanged; a flashing/charging graphic is not promised for this meter.

## Next short parked test

1. Close Y-Connect and disconnect nRF Connect. Ignition on, engine off.
2. Let the saved map connect. Verify live data starts on its own without a second bike selection.
3. Stop bike data and wait at least thirty-five seconds. It must remain stopped while that map connection remains open.
4. Reconnect the map, verify automatic data resumes, then run the additional-field check once.
5. Compare phone readings with the `Phone status sent to bike` counters. If checking charging, unplug USB briefly and restore it, observing the exact native graphic independently.
6. Save private test log, stop data and map sharing, ignition off. Export the Bluetooth recording with ignition off.

Three reconnects, over two minutes of screen-off fresh data, repeated map zoom, navigation views and native headset transitions were confirmed on 0.12.48. They are prior-build evidence, not automatic passes for this build.

## Brake and indicator research

Source review identifies known local-record byte 6 as battery voltage and byte 18 as atmospheric pressure. Repeated holds rejected the earlier byte-18 candidate. Byte 21 remained zero throughout the captured brake/indicator states. Generic SDK pressure locals from a different CCU family are not enabled on this bike. No brake pressure, brake-switch or indicator decoder has been validated. Battery sag must not be recorded as measured braking or insurance evidence.

Source tables distinguish LinkCard 4.2 from Linkbox2; the shared MotoCons service alone does not identify the family. Current captures also include CAN 689/698/1028, so earlier claims that these IDs never reply must not be reused. No original APK, decompiled implementation, raw capture, peer identity or authentication details belong in public source.
