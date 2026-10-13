# RideDeck for Yamaha 0.12.35: parked test update

- Full-height destination search supports stores, streets, saved Home/Work/favorites and pasted coordinates. Optional TomTom search falls back to the existing providers. Google Maps sharing still requires confirmation of the destination pin.
- Optional TomTom motorcycle route alternatives use traffic information and reject returned motorway/toll sections. Route planning keeps a large overview with parking suggestions. Nearby fuel stations are available on the phone and in the bike menu. Fuel prices require separate provider access and are never invented.
- Nearby traffic reports show their provider and check time. Traffic-aware route refresh can be disabled in Map and routing accounts. Provider quotas and charges still apply.
- Ride book saves local ride history and timestamped all-time peaks for GPS speed, GPS acceleration/deceleration and calibrated mounted-phone lean estimates. These are phone estimates, not Yamaha IMU data or brake-force measurements.
- Repeated bike zoom presses advance the map revision, send updated native zoom state and redraw the last available map position even while parked. This fixes the software paths identified in the one-press failure. Handlebar behaviour still needs a bike test.
- Bike data and fuel range includes an independent parked connection test for the observed XMAX accessory service. It obtains connection credentials through the paired Yamaha channel, encrypts them on the phone, attempts accessory authentication, checks supported fields and requests raw snapshots. It stops when its page closes or after ten minutes. It preserves existing pairing.
- A private local test log contains vehicle field IDs, raw bytes, receive times and manually marked control states. Authentication packets and pairing credentials are excluded. Saving it is an explicit phone action.

## Limits before the parked test

Accessory authentication and independent snapshots have not yet passed a live RideDeck bike test. Physical brake/indicator meanings, fuel level/consumption, counter reset behaviour and model-specific units remain unverified. Fuel range stays unavailable until trustworthy remaining-fuel and consumption inputs exist. A byte changing after a brake press is not automatically a brake signal.

The existing phone music controls, English navigation voice, bike map layout, clock/arrival/remaining-time cycling and solar map theme remain available. The separate RideDeck Standard and RideDeck Dash apps are not updated by this release. The social video editor remains in the paused Dash scope.

Private captures, third-party APKs, decompiled sources, owner credentials and research bundles are excluded from this repository and release artifact.
