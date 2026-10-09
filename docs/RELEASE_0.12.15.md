# RideDeck for Yamaha 0.12.15

Axel authorized this update with "time to update and push". Yamaha app only; package and signing identity remain compatible with the installed app.

- Arrival time and remaining travel duration on the map and below the large phone instruction, alongside the music player. Native ETA data accompanies turn guidance. Estimates use route-provider timing without live traffic.
- Road-sign speed-limit icon where the map provider supplies a numeric posted limit on the matched route. Hides unknown, stale or uncertain values. Coverage depends on mapped data.
- Light/dark map follows the calculated sun position at the GPS location, with native day/night updates. Both map styles are warmed for switching.
- App screens stay awake while visible. Navigation and bike foreground services protect active work, restore saved sessions after Android-managed recovery, and retry transient bike disconnects. Explicit Stop remains authoritative. Setup includes background battery-access help; manufacturer settings may still be needed on the phone.
- Message-indicator groundwork derives pending/clear/unavailable state from the same selected-message queue as the phone. Seen / next clears it after the last pending preview. This is local groundwork for future native accessory support.

Yamaha's native message, signal, battery, App connection and headset icons are **not restored in this update**. Verified battery/signal/headset encoders remain inactive. Their accessory connection and notification indicator/clear behavior still need bike evidence. No message content is transmitted by this groundwork. Details: YAMAHA_STATUS_ICONS.md.

Local validation before signing: all 72 unit tests passed with zero failures/errors and the debug build passed. These checks cover navigation packets, route timing, speed-limit matching, solar calculations, saved-destination validation, accessory status packets and message lifecycle. They do not establish physical bike or phone behavior. CI/signing results and installed APK provenance are recorded separately during delivery.

Install as an update to RideDeck for Yamaha without uninstalling it, preserving app settings and permissions. Phone and parked-bike tests are required for the new visual, lifecycle and native navigation behavior.
