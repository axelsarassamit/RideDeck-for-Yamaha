# Pending local changes

Release hold requested by Axel: no GitHub pushes, remote signing runs, published updates or phone installs until the list is finished and Axel explicitly says "time to update and push".

Axel has now explicitly said "time to update and push", lifting this hold for 0.12.15. Implemented features are authorized for the update, with phone/bike behavior still needing physical verification. The native Yamaha status-bar icons remain unfinished and are not included as working features. See RELEASE_0.12.15.md for the update scope.

## Native Yamaha status-bar icons

Requested: message bubble, cellular signal, phone battery, App connection and headset, matching Axel's bike photo. **Unfinished; this item must not be marked ready for release.**

Confirmed that these require Yamaha's accessory connection independently of the Garmin map stream. Added a separate, inactive codec for the verified battery, signal and headset BLE messages, with packet fixtures and unknown-state handling. Native App connection and notification indication still need the matching accessory session and bike evidence. No substitute icons are drawn into the navigation image.

Message icon clarification: sync with RideDeck's selected pending-message queue. Added a metadata-only listener API returning PENDING, CLEAR or UNAVAILABLE from the same inbox used by the phone panel. Seen / next clears the icon state only after the last pending message; new content can relight it. Source deselection, inbox clearing and listener loss follow the same app state. Android notification dismissal retains the pending preview as before, so the icon also remains pending until Seen / next. Native transmission is still blocked on the verified accessory connection and indicator/clear protocol.

Verification after the message-sync clarification: all 72 unit tests passed, zero failures/errors, and debug build passed. Four new inbox lifecycle tests cover multiple pending apps, final acknowledgement, unchanged refresh, new content, source deselection/removal/clearing, unavailable listener/recovery and stale-reader acknowledgement. No bike icon behavior has been tested or installed.

The blocker is the actual CCU accessory endpoint and authentication/notification behavior. [Detailed evidence and remaining work](YAMAHA_STATUS_ICONS.md). Physical verification is required after the release hold is lifted. No phone or bike changes were made for this item.

Local verification: all 68 unit tests passed, with zero failures/errors, and debug build passed. The four new codec tests cover verified battery/charging packets, all six signal states, headset connection/clearing, and unavailable/malformed values. Static call-site check confirms the new codec is not connected to a runtime Bluetooth writer. These checks do not demonstrate native icons on the bike.

## Arrival time and remaining travel time

Implemented locally, awaiting release authorization and bike verification.

- Arrival clock time and remaining travel duration on the bike map, above attribution.
- Same estimate below the large phone instruction, with the music player retained.
- Native XMAX ETA service 1 updates alongside native turn guidance and lists. Wire format verified against installed StreetCross 1.87: POINTER, UInt32 little-endian minutes after midnight.
- Uses Valhalla maneuver travel times and progress through their geometry. Falls back to total route duration scaled by remaining distance when detailed timing is incomplete.
- Recalculates for a new route and stops showing the previous phone estimate during rerouting, GPS loss or route clearing. Native ETA writes pause with GPS loss or recalculation; native clearing follows the existing route-inactive status.
- Next-day arrivals include the local weekday on the phone/map; native ETA is a clock-of-day field.
- Estimate follows route-provider timing. No live traffic feed is available.

Local verification: all 44 unit tests passed, including packet fixtures, uneven section timing, missing/overlapping timing, bounds, arrival at destination, duration formatting and midnight rollover. Debug build passed. No installation or hardware test performed under the release hold.

## Mapped speed-limit icon

Implemented locally, awaiting release authorization and bike verification.

- Red-ring road-sign icon with numeric km/h limit on the bike map and phone map/arrow view. The same icon sits beside arrival time in the phone music panel.
- Valhalla trace_attributes fetches posted speed_limit values for the next route window (about 3 km, capped at 400 segments). Requests use a separate worker, with 30 seconds between window lookups within a route, and cannot block route calculation or map rendering.
- Only exact matching route geometry and kilometer units are accepted. Speed estimates, missing values, zero, unlimited sentinels and malformed values do not become limits.
- Hides the icon for unknown sections, GPS loss or uncertainty, off-route positions, heading disagreement, arrival, route clearing and recalculation. Old-route responses cannot overwrite a new route.
- Native XMAX service 17 uses float LE, unit byte length and UTF-8 km/h, POINTER. Zero clears the limit. Wire format and zero behavior verified against installed StreetCross 1.87 packers at 0x67a3c and 0x67e24.
- Live public test route in Berlin returned distinct 30 and 50 km/h sections, plus sections without limits. No personal route was sent for this test.
- Data coverage and freshness depend on the provider's OpenStreetMap data. No camera recognition, temporary-sign recognition or overspeed alert is added.

References: [Valhalla speed-limit semantics](https://valhalla.github.io/valhalla/concepts/speeds/), [trace_attributes API](https://valhalla.github.io/valhalla/api/map-matching/).

Local verification after both pending features: all 54 unit tests passed and debug build passed. Recorded public-provider geometry replay verifies 30/50/30 section boundaries even with a repeated endpoint. Additional cases cover unknown/unlimited values, overlaps, changed geometry, uncertain positions, and native update/clear packets. Nothing pushed, signed remotely, published or installed.

## Automatic sun-based map appearance

Implemented locally, awaiting release authorization and visual/bike verification.

- Light map when the sun is up; dark map when it is below the calculated sunrise/sunset horizon. Uses current UTC time and the GPS coordinates, including seasonal and latitude differences, leap years and polar day/night.
- Offline solar-position calculation uses the NOAA fractional-year equations and 90.833-degree sunrise/sunset zenith. It is an approximate astronomical horizon, not an ambient-light sensor or a weather detector.
- Uses official MapTiler streets-v4 and streets-v4-dark styles, keeping English labels and provider attribution. Guidance and arrival bars, attribution text, route colour, arrow and waiting views follow the map mode.
- Both styles are warmed and retained in memory for the navigation session. Switching cancels the old renderer but keeps the latest map image until the replacement arrives. Failed style loading preserves the working map; style requests use a worker separate from route calculation.
- If GPS temporarily stops, the last coordinates still determine the sun. Before any usable position, the last solar mode or phone appearance is used.
- Native service 31 sends a VALUE payload DAY=1 or NIGHT=2 when the mode changes, including while images are paused. Codes verified from installed StreetCross 1.87 NaviLiteDayNightModeType constants and its native packer. The phone music panel remains as configured.

References: [NOAA solar equations](https://gml.noaa.gov/grad/solcalc/solareqns.PDF), [official MapTiler style IDs](https://docs.maptiler.com/sdk-js/api-reference/variables/Externals.MAP_STYLE_CONFIG/).

Local verification after all three pending features: all 61 unit tests passed and debug build passed. Solar cases cover Bangkok sunrise/sunset, longitude changes, both sides of the date line, polar day/night in both hemispheres, leap day, UTC midnight, phone timezone changes and invalid coordinates. Native day/night packet fixtures passed. Map style IDs verified against official provider documentation; actual visual rendering, native bike response and physical testing remain pending. Nothing pushed, signed remotely, published or installed.

## Stay awake and continue in the background

Implemented locally, awaiting release authorization and phone/bike lifecycle verification.

- Every RideDeck activity keeps the screen on while visible, including Setup. Leaving the app permits normal screen locking while the navigation and bike foreground services continue.
- Active services use renewed, timed partial CPU wake locks; they release them when stopped. Bluetooth retries release their lock during the 45-second wait. A destroyed service cannot reacquire a closed lock.
- Both services survive removal from Recent apps and return START_STICKY for Android-managed process recovery. Private saved session state lets navigation recalculate the last requested destination after a fresh GPS fix and lets the bike reconnect only to the already-selected paired device.
- Transient Bluetooth loss retries in the existing foreground service without needing the phone UI. Each attempt closes its socket and receiver before another starts; stale receiver sockets and acknowledgements cannot enter the new connection. Revoked permissions, removed pairing and incompatible displays stop retries.
- Explicit Stop clears the corresponding saved session, closes resources and stops CPU protection. Stop route clears the saved destination while GPS may stay active. No boot receiver, screen unlocking or force-stop bypass is added.
- Setup > Keep running provides Android's background battery-access request and app-settings link. Xiaomi/HyperOS may additionally require No restrictions and Background autostart, chosen on the phone. No phone settings were changed during this local work.

References: [Android keep-screen-on behavior](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on), [foreground wake locks](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock), [sticky service recovery](https://developer.android.com/reference/android/app/Service#START_STICKY), [device background restrictions](https://developer.android.com/topic/performance/background-optimization).

Local verification after all four pending features: all 64 unit tests passed and debug build passed. Resume-state tests reject stopped sessions and malformed coordinates, preserve the exact destination and accept legitimate zero coordinates. Merged manifest verifies wake permission, background battery access and both foreground service declarations with stopWithTask=false. Actual screen-off, Recent apps removal, Android process recovery, explicit Stop and bike reconnect tests remain pending on the phone. Nothing pushed, signed remotely, published or installed.
