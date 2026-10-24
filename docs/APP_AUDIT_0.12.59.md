# Yamaha 0.12.59 app audit

Closing Yamaha with Back from the main screen or removing its task now stops navigation, bike data, map sharing and Yamaha's ride-recording lease. Home, screen-off and rotation preserve an active ride. Queued service starts cannot restart a closed session. Android may retain a cached process without active ride services.

Route selection now supports the shared ripple-button background without a drawable cast crash. Editable reply drafts survive rotation without writing message content to saved state or logs. Permission and older-Android API handling were corrected across location, phone status and message replies.

Validation on the final source:

- 247 unit tests passed.
- 54 connected-phone checks passed, including actual Back/task removal, queued service starts, reply rotation, route selection, favourites, menus, map controls, simulated bike rendering and public Bangkok route generation.
- Android lint passed with zero errors. Nonfatal warnings remain, principally UI string and Kotlin modernization suggestions, deprecations and dependency updates.
- Encoding checks passed.

CI now runs unit tests and lint in addition to building and checking compiled text.

These checks do not replace physical-bike or road validation. Real Thai U-turn/lane guidance, real calls/headset/recording handoff, calibrated fuel range and unconfirmed brake/indicator fields remain separate validation work. No new motorcycle control commands were introduced.
