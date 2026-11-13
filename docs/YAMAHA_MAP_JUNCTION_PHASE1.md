# Yamaha map and junction display: first implementation batch

This batch implements the native display layout and initial cache reliability work. It is not acceptance of the entire navigation improvement plan or proof of motorcycle compatibility.

## Display behavior

The actual MapLibre map occupies the centre. A provider-derived lane schematic appears on the left within 300 metres of the active maneuver and stays visible to 350 metres to prevent boundary flicker. It is hidden when guidance is stale, lanes are unavailable, the maneuver changes, or the maneuver is passed. Lane sections must bind to the same maneuver as the distance shown beside them. This is a schematic of lane recommendations, not a photorealistic junction image or sensed road occupancy.

The right guidance rail keeps fixed bounds at both 480×234 and 480×240. Long instructions scale within those bounds. ETA and estimated fuel range alternate in the same footer every ten seconds; a sole available value stays visible. Unknown values stay unavailable. The firmware's left zoom strip remains reserved. No firmware clock or instruments are recreated.

Phone instruction bounds also stay fixed, with a full-instruction dialog. The phone's existing native trip HUD owns fuel information, avoiding a second fuel label in the map bitmap. Unchanged, confirmed stationary maps have a two-second refresh target; movement or changed presentation bypasses that idle interval. A reliable session heading is retained when stopped.

Unavailable/recalculating guidance does not display an old turn arrow or lane recommendation. Expired GPS immediately invalidates both map surfaces even if their pixels were captured recently. A late snapshot from an expired captured fix is rejected before publication. The usable road image remains available as a clearly paused last map.

## Navigation actions and search

Both riding tools and flat map settings distinguish **Clear route** (keep the free-ride map session) from **Stop navigation** (end the navigation service). Clearing a route immediately invalidates old guidance and resets off-route/junction state.

Search prefers a valid navigation position, then valid recent system last-known positions, before a provider's area fallback. Positions must be no more than 60 seconds old, not future-dated, geographically valid, and have known accuracy within 1 km. Approximate-only permission cannot reuse a previously precise navigation fix; only the system-authorized network position is considered. Permission and age are checked at each provider/query boundary, including after a slow fallback. No search action starts background location or a riding service. The search page explains when no recent local anchor is available.

## Cache behavior and limits

MapLibre's ambient resource cache is bounded to 256 MiB, or 64 MiB below 1 GiB of available storage. Startup continues after a bounded cache-configuration timeout. The last usable frame is retained during replacement loading; route/view revisions reject obsolete callbacks.

Day and night style documents are separately encrypted using Android Keystore, bounded in size, and tied to provider/style identity and expiry. Explicit no-store/no-cache directives prevent reuse. Declared max-age is capped at 24 hours; absent freshness metadata uses a one-hour private cache lifetime. Provider URL logging is suppressed.

Ambient caching only retains resources actually fetched. It does not establish complete route/area coverage, offline search/rerouting/traffic, or permission to bulk-download provider tiles. Intentional offline packs and rich junction imagery remain separate, provider-dependent work.

## Verification

The batch passes 267 JVM tests and 17 Android regression checks on the physical phone. Lint reports zero errors, 264 warnings and two hints. Android regressions cover native viewport boundaries, fixed phone instructions, encrypted cache reuse/identity/expiry/corruption, live public motorcycle routing, route guidance, flat settings, distinct route/stop actions, immediate GPS-expiry invalidation, and host survival when an optional appearance-sync peer dies. The standalone fault provider lives only in the test APK.

An explicit opt-in preview test renders a public Bangkok fixture using actual TomTom lane data and MapLibre resources. DEMO road labels, ETA and estimated range are synthetic; these images are app-rendered frames, not photographs of a connected motorcycle. Both native resolutions and day/night footer phases are captured. No personal location is used.

The emulator's real-map snapshot times out and its live-route request later failed DNS resolution; the same checks complete on the physical Android phone. Emulator regression success must not be presented as successful emulator map rendering. An existing settings test also expected the previous heading, "Guidance", instead of the baseline's "Guidance & Quick Actions"; its assertion now matches the actual section. Failed runs are retained in local validation evidence. Parked Bluetooth/display delivery, maneuver timing on the motorcycle, broader Thai junction coverage, complete offline coverage and the remaining navigation improvement tasks still require separate acceptance.

On 2026-11-13 an initial physical-phone pass completed the public online preview in 6.534 seconds and the warm-cache preview with SDK networking disabled in 5.375 seconds. Eight frames were produced per pass. Six cached frames were pixel-identical to their online counterparts; the remaining two differed by two pixels each. This is evidence for the already-warmed public viewport, not an entire route, cold offline startup, or offline route calculation. The phone's connectivity settings were not changed.

To run public preview checks explicitly, use the existing preview test APK with `phase1PublicMapPreview=true`. After warming the same public views, `phase1MapOffline=true` forces MapLibre alone offline and reads encrypted cached styles. It restores automatic SDK connectivity afterward and never changes device Wi-Fi/mobile settings. Provider setup is private and must never be committed or included in screenshots/logs.

GitHub validation must match the pushed source commit. A prior green main build does not verify this batch. No signed production rollout is implied by local test results.
