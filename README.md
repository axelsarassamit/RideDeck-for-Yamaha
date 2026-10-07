# RideDeck for Yamaha

Native MapLibre navigation and Bluetooth transport for compatible Yamaha dashboards. Independent map, turn arrow, and music choices for phone and dashboard.

Repository: https://github.com/axelsarassamit/RideDeck-for-Yamaha

App name: **RideDeck for Yamaha**

## Build

Java 17 and Android SDK 36. Run `./gradlew assembleDebug` or `./gradlew assembleRelease`. No flavor selection is required.

Signed releases require the four GX12 signing secrets in the release workflow. Secrets have not been copied from the historical repository because GitHub cannot return stored secret values. Updating existing RideDeck installations requires the original signing certificate.

No tests or bike verification were performed for this split. Provider keys are never committed. See THIRD_PARTY_NOTICES.md for licensing.
