# RideDeck for Yamaha

Native MapLibre navigation and Bluetooth transport for compatible Yamaha dashboards. Phone music controls and messaging accompany the bike map, turn-by-turn and turn-list views. Standard RideDeck and RideDeck Dash are maintained separately.

Repository: https://github.com/axelsarassamit/RideDeck-for-Yamaha

## Build and release

Use Java 17 and Android SDK 36. Run `./gradlew testDebugUnitTest assembleDebug`. Debug builds use a separate preview package. Release builds require the original signing identity; do not replace an installed production app with a different key.

The private signing service builds an exact committed source revision and returns the APK, checksums and source provenance. The current automated request is `signing-requests/yamaha.json` in `axelsarassamit/gearelec-gx12-companion`, using the exact source commit and numeric version. The manual helper `./scripts/Build-Signed.ps1 -Version 0.12.41` remains available through the edition-signing workflow. Private signing keys stay in GitHub secrets. Each app publishes its own releases.

## Navigation and provider configuration

MapLibre renders map tiles from MapTiler. TomTom optionally supplies place search, traffic-aware motorcycle routes, incident reports and fuel-station data. Without TomTom routing, Valhalla uses a public fair-use service without live traffic. Provider availability, account access and quotas apply. GraphHopper is no longer used.

Signed personal builds may bundle a MapTiler default. User-entered MapTiler and TomTom keys are encrypted on the phone, and overrides take precedence over bundled defaults. No keys belong in Git source or logs. Embedded mobile credentials can be extracted from an APK. See THIRD_PARTY_NOTICES.md for licensing.

## Verification and bike diagnostics

Release notes in `docs/RELEASE_<version>.md` distinguish automated checks from actual phone and motorcycle testing. Y-Connect authentication and supported bike telemetry remain under investigation. Keep decoder fixtures and protocol research separate from confirmed live-bike support. Raw captures and authentication material stay private.
