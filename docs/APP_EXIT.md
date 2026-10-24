# Closing Yamaha

Back from the main screen and removal from Recent apps stop navigation, location updates, bike display sharing, bike data, reconnect attempts and Yamaha's ride/companion session. Ride time and bike history are retained; active navigation resume state is cleared. Independent Dash recordings are not changed.

Notification and call monitoring is unbound on explicit close without revoking access. Opening an app screen restores monitoring. Closed state survives process recreation and blocks queued foreground starts and automatic reconnection; queued foreground requests are acknowledged before stopping to avoid an Android foreground-service timing crash.

Home, screen off, child-screen navigation and rotation do not count as explicit close. An active ride may continue through them. Android may retain a cached process after closing; the acceptance check is absence of active services, GPS work, reconnect attempts and wake locks, not absence of a process name.

Preview-device checks cover actual Back input, actual task removal, background/rotation, rejected queued restart and successful reopening. No bike connection, route request or recording is started by these checks.
