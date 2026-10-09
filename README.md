# RideDeck for Yamaha

Native MapLibre navigation and Bluetooth transport for compatible Yamaha dashboards. Phone music controls and messaging accompany the bike's map, turn-by-turn and turn-list views.

Repository: https://github.com/axelsarassamit/RideDeck-for-Yamaha

App name: **RideDeck for Yamaha**

## Build

Java 17 and Android SDK 36. Run `./gradlew assembleDebug` or `./gradlew assembleRelease`. No flavor selection is required.

Signed builds use the historical repository signing service to preserve the original certificate. Run `./scripts/Build-Signed.ps1 -Version 0.12.0` from this project with GitHub CLI authenticated. The service builds the exact committed HEAD and returns an APK, checksums, and source provenance. Private signing keys remain in GitHub secrets. Each project owns its source and update releases.

Version 0.12.15 adds arrival/remaining-time estimates, mapped speed-limit signs, sun-based light/dark maps and background-session support. All 72 local unit tests and the debug build passed before this update. New phone lifecycle and bike behavior need physical verification. Yamaha's native phone-status icons remain unfinished; the navigation image connection does not provide the accessory status bar. See docs/RELEASE_0.12.15.md for details. Provider keys are never committed. See THIRD_PARTY_NOTICES.md for licensing.


## Personal provider configuration

Signed Yamaha builds inject the personal MapTiler and GraphHopper keys from the private signing repository secrets. Keys are absent from Git source and logs, but embedded mobile credentials can be extracted from an APK. These personal builds use the owner's provider quotas. User-entered encrypted keys take precedence over bundled defaults. The phone edition has no provider keys or Yamaha logo.
