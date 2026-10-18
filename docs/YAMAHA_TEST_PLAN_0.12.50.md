# Yamaha 0.12.50 parked verification

Scope: combined phone status and optional native music. Existing map/data auto-connect, authentication, pairing and liveness are preserved. No engine start is needed. Standard/Dash are outside this update.

1. Ignition on, engine off. Close Y-Connect and disconnect nRF. Let map and data connect automatically. Keep native music test off. Verify fresh readings and five minutes of keepalive stability.
2. Open Check phone status. Confirm Combined phone status has a successful write. Compare readings with Android. Unplug USB ten seconds, reconnect ten seconds; observe native charging appearance. Repeat once. Save the private log. If the graphic never changes, mark Failed even when both charging flags were written.
3. Switch headset off/on and confirm native symbol follows it. Keep phone Bluetooth on. Recheck map zoom and all three navigation views.
4. Start music in the selected phone player, with existing music notification access. Enable Native bike music · test in Bike data. Open Yamaha's built-in music screen. Check title/artist, next/previous/pause/play and two track changes. Phone music controls must keep working. Verify data freshness and zoom while music is active. No audio-routing changes are expected.
5. If the native music transfer times out, disable Native bike music · test, stop data, reconnect and save the log. Do not repeat an unsupported transfer indefinitely.
6. Disable the optional music test, save the private test log to Downloads, stop navigation/data/map sharing and turn ignition off. Keep USB attached until the capture is verified saved. Record results manually in the build's Yamaha test panel.

Outstanding: native charging behavior and native music firmware behavior require this test. Brake/indicator bits, pressure, IDs3/9 and fuel-counter units remain unresolved. Do not repeat brake holds or infer fuel range from raw values for these checks.
