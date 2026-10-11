# RideDeck for Yamaha 0.12.28

The ride page offers **Start Dash automatically** when the genuine signed, enabled Dash app is available. It defaults on and remembers the rider's choice. Explicit Start or Resume opens Dash once after an authenticated active lease is established; Dash's own Auto record preference, permissions and power rules decide whether to record.

The pending open is cancelled by Pause, End, closing the page, leaving the activity, a newer ride session, or a ten-second timeout. A delayed reply cannot consume a newer Start gesture. Service heartbeats, background restarts and notification actions never open Dash. No camera or recording commands are sent by Yamaha.

Without a trusted, launchable Dash installation, all Dash-specific controls including this setting stay hidden. The ordinary ride timer remains available. Yamaha branding, cockpit layout and the original signing identity are preserved.

Validation: local policy tests cover authentication rejection, timeout, hidden UI, cancellation, duplicate/stale replies, session transitions and missing or disabled Dash. Both repositories' exact Android workflow commits and the matching signing artifact are verified separately. No phone tests or installation are performed until the coordinated changes are complete.
