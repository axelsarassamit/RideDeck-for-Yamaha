# RideDeck for Yamaha 0.12.26

A visible Dash button opens large Start ride, Pause/Resume ride, End ride and Open Dash controls. The user starts a ride explicitly; GPS startup, route planning, an open cockpit and the bike connection alone do not enable Dash in the background. Recording is started separately inside visible RideDeck Dash.

The user-started ride timer runs in a dedicated non-sticky foreground service with a visible notification and Pause/End actions. It renews the authenticated companion v1 lease every five seconds. Pause and End stop renewal and clear only Yamaha's lease. Process death does not restore an active ride; Dash's 15-second lease expiry provides the fallback. Removing the activity from Recent apps leaves an explicitly active foreground ride running, with visible notification controls.

Provider calls are serialized off the UI thread. Missing Dash, unsupported replies, rejected ownership and authorization failures leave normal Yamaha functions available. The integration neither starts cameras nor sends speed telemetry. The new Yamaha launcher icon and navigation behavior from 0.12.25 remain intact.

Protocol: `content://com.axelsarassamit.ridedeck.dash.bridge`, `protocolVersion=1`, `renew`/`stop`. Dash authenticates the caller's installed package and signing certificate. No active session is persisted or restored by an activity, boot or app update. Android service declaration follows the [special-use foreground service contract](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).

Validation covers idle/active/paused/end timer state, duplicate starts, process loss and incompatible or rejected bridge replies. Signed-build and coordinated phone checks are recorded separately.
