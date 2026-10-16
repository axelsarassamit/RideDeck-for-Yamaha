# Yamaha test plan 0.12.48

Scope: RideDeck for Yamaha, 2024 XMAX 300. Standard and Dash development and Dash video analysis stay paused. No bike trip is required to install and check the panel indoors.

## In-app checklist

Open Setup > Help & diagnostics > Yamaha test panel, or Navigation > Bike data and fuel range > Yamaha test panel.

42 checks contain steps and pass criteria. Choose a group, show unfinished checks or open Next unfinished check. Mark Pass, Problem, Blocked / not supported, or Save notes / test later. A blocked check is never a pass. Notes and results persist locally for the exact version name and version code; a new build starts untested while old observations remain available in the exported report. Reopening a passed check allows correcting it. Unsaved draft notes survive rotation. Export uses Android's Save document picker, never an automatic upload. Notes are user-entered and may contain personal information: review before sharing.

Connection metadata shows authentication, elapsed session time, received packet count/age, supported vehicle IDs and keepalive requests versus successful reply writes. A write completion is not proof the bike accepted the reply. Counts never automatically pass a test. The phone-status preview is a separate real Android readout, not synthetic bike telemetry.

## Order and stop gates

1. **Indoors:** signed build identity, panel actions/persistence/export, phone-status readings, menus and permissions, search/sharing/coordinates, large route preview and zoom, English sample voice, reply draft/cancel, map layout and traffic availability. No external messages are sent during automated checks.
2. **Parked gate, about five minutes:** ignition on, engine off; Y-Connect closed and nRF disconnected. Connect once and leave connected for five minutes. Require continuing fresh data and matching keepalive replies. Save the private test log before Stop. Any unexpected disconnect fails the gate; save its time and exact status. Do not explain status 19 as routine sync completion or mask it with automatic retries.
3. **Parked functions, short blocks:** only after the gate passes, test three explicit reconnects, ignition-loss labelling and manual recovery, native phone-status icons, repeated bike zoom, all view changes, destination/menu actions, music and independent map plus data operation. Split blocks and turn ignition off when finished. Record Stop before ignition off so these events are distinguishable.
4. **Background:** two-minute screen-off check, continuous data enabled explicitly and restored afterwards. Default parked data intentionally ends at ten minutes. Do not call that expected limit a failure or leave ignition on for a needless long soak.
5. **Data comparison:** engine-off RPM, odometer and independent available voltage/temperature references. Record absent replies separately from zero. Observe faults without creating/clearing them. Brake/indicator differential observations only after streaming is stable; mark each action, repeat three times, one control at a time. No engine-running step is necessary in this first session.
6. **Later road check:** prepare route and headset while stopped. Check GPS, heading/speed, guidance timing, reroute, ETA/distance and arrival during a normal safe ride; review logs afterwards. No phone operations while moving. Any engine-running calibration is a separate later test outdoors, not implied by an engine-off check.
7. **Longer calibration:** fuel units, refill anchors and sensor-derived lean/deceleration require independent references and repeated rides. No fuel range or insurance-grade measurement is claimed merely because raw bytes or phone estimates are available.

## Coverage

- Connection: authentication, five-minute stability, keepalive, stop/reconnect, loss, freshness, screen-off, map coexistence. Never unpair/reset to make a test pass.
- Native header: real battery/charging, cellular default data SIM, headset connect/disconnect and CCU exclusion; native App state independent of projected map. Verify unknown, permission-denied and unavailable readings without invented zeros.
- Other icons: inventory App, message, call, Bluetooth, clock and temperature against official Y-Connect in separate sessions. Temperature is bike-owned. Message/call/weather status requires verified wire behaviour and remains unimplemented, not silently ticked off.
- Bike buttons: at least ten distinct plus and minus presses; view cycle twice, turn-list scrolling, Home/Work/Favourites/fuel stations/Stop. Check labels are unobstructed.
- Navigation: street/shop/home results, Google Maps sharing, decimal coordinates, motorcycle parking access, alternatives and map overview, clock cycle, one speed sign, attribution, Unicode, day/night, incident feed availability and actual incident rendering.
- Voice/messages/music: English samples and live guidance separately; draft/edit/cancel and reply method; agreed recipient/text required before a real Send; native bike music with phone controls, music volume recovery after speech. Dash compatibility remains deferred while Dash is paused.
- Ride book: timestamps, source labels, maxima, distance, phone estimates versus bike readings, stale/unavailable handling.
- Release: exact source builds and original-key signing, version/package, preserved saved settings and pairing. Physical results remain pending despite successful CI.

## Qwen roadmap disposition

The supplied roadmap is research input, not a verified specification for every model. Received bytes establish reception, not the physical meaning or unit of every field.

| Roadmap area | Current handling / acceptance condition |
| --- | --- |
| Auth and transport | Fresh accessory exchange retained; Classic remains open; keepalive fix retained. Validate stability first. No burst/resync policy inferred from status 19. |
| Live engine and odometer | Existing guarded decoder and UI. Only expected XMAX record lengths accepted. Dashboard/reference comparisons remain necessary. |
| Capability list | Supported IDs now visible in test panel. Live subscriptions remain restricted to decoded IDs 1 and 72. IDs 3 and 9 require parser evidence before enabling. |
| CAN/service records | Known allowlisted raw records retained and visible in Bike data. Meanings and units are research checks, not new dashboard measurements. |
| Faults and injection | Existing selected-query notices and raw injection counter. No fault clearing, fuel gauge or remaining-litres claim. |
| Phone battery, signal, headset | Real Android runtime monitor wired after authentication; deduplicated/forced status responses, lifecycle cleanup, unknown omitted. Native rendering still needs parked test. |
| Identity and warning history | Source review required for individual query/parsers and privacy boundary. Not enabled by the checklist. VIN is excluded from diagnostic exports. |
| FFD/history and extra engine fields | Need exact per-model widths, null/sentinel rules and units. Remain blocked research until corroborated. |
| Datetime/thermal | Not guessed from battery packets; need exact serializer/model semantics before adding outgoing data. |
| Message/call/weather/icon arrangement | Capability advertisement alone does not establish payload semantics. Verify exact request, response and clear behaviour before enabling. No blanket probe button. |
| YRC/lap/photo/sport features | No evidence this XMAX supports them. Remain unavailable rather than presenting working controls. |
| Firmware/factory/unknown CAN | Excluded. No such commands in test panel. |

## Completion and next move

Release readiness requires correct signed identity, passing automated checks, the five-minute physical connection gate and the relevant phone/native UI checks. Unsupported research stays explicitly blocked with evidence; it does not inflate the pass count. Use exported failed/blocked check IDs and private logs to choose the next feature, rather than adding broad speculative commands.

## Second suggestion sheet review

The 2026-10-16 suggestion sheet adds session-window research, private fixtures, a capability cache, mechanic fields, battery health/crank trends, thermal graphs, fuel calibration, a DTC journal, maintenance reports, lap timing, audio scheduling, ride-event tagging, an experiment runner and sync history. These now have checklist entries or are covered by the existing fuel/control checks. Future features explicitly say Not available in this build; Pass is disabled for those entries. They can be recorded as blocked with notes. This is a testing backlog, not a claim these features are implemented.

No all-frame ring logger is added: a denylist of authentication commands alone cannot exclude credentials or identifying message content elsewhere. Positive allowlisting and sanitized fixtures are required. No automatic resync policy is added, no crank/battery-failure diagnosis is inferred, and fuel-consumption drift cannot establish injector clogging. Firmware/factory/actuator writes stay absent, rather than adding a developer switch.
