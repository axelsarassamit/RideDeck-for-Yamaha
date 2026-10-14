# RideDeck for Yamaha 0.12.36

This test update completes the indoor implementation of the Yamaha CCU reader and brings the pending destination search, traffic, fuel-station and ride-book work into one installable Yamaha build.

- Independent accessory-authentication and read-only startup use the owner's observed MotoCons service, server 1248 and supported-field discovery. Optional metadata, diagnostic, CAN and injection-counter requests cannot block a working live engine stream. Unsupported or malformed layouts are rejected.
- Large bike-data cards show source-decoded RPM, speed, voltage, temperatures, throttle and four-byte odometer readings with receipt times. Disconnected or old values are explicitly marked as last known.
- A user-started foreground reader supports a ten-minute parked test or an explicitly selected continuous ride session. Stop and disconnect release Bluetooth resources and the wake lock. There is no automatic connection on boot.
- Ride history upgrades without deleting old rides. Fresh CCU values retain source, unit, timestamp, minima and maxima. Approved raw fields and user-marked control observations remain separate from decoded values and phone estimates.
- Private diagnostic logs exclude credentials, VIN and arbitrary metadata. Logs are bounded and exported only through a deliberate phone action.
- Repeated map zoom detaches canceled renderers before callbacks and rejects frames for older camera requests. Native feedback is sent for every bike zoom press, including a limit press.
- U-turns retain distinct left/right dashboard icons and English voice guidance. Destination search respects system screen insets.
- The 0.12.35 search, store/POI results, coordinates, Google Maps sharing, large route overview, motorcycle alternatives, optional TomTom traffic and fuel-station search remain included. Fuel prices require separate provider access and are never invented.

The next parked-bike check must verify independent authentication, compare live values with the actual dashboard and exercise navigation buttons. Brake/indicator bit meanings and fuel-counter units are still unverified. Remaining fuel and time to empty require a confirmed model, not an assumed gauge. Phone acceleration, deceleration and lean are estimates, not measured bike brake force or insurance-certified data.

See [the parked-bike guide](PARKED_BIKE_GUIDE_0.12.36.md). Raw captures, provider credentials, APK research copies and decompiled sources stay outside this repository.
