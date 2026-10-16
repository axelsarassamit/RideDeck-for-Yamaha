# Yamaha connection test 0.12.46

The 0.12.45 parked-bike attempt found the selected bike but timed out before its BLE data connection completed. Discovery permission was granted. Authentication was not reached.

This follow-up tests one connection-session change: open the existing paired accessory channel, request fresh connection details with the observed information request, and retain that channel while connecting to BLE, authenticating and reading bike data. Stopping, cancellation or failure closes the channel. Previously a cached-details reconnect skipped the channel, or the fresh-details path closed it before BLE discovery.

The reference app's observed code retains its accessory channel during BLE setup. Whether that lifetime is required by this bike is unverified. This build does not reset pairing, initiate new bonds, or add actuator commands. Existing stored details are untouched; fresh credentials remain in memory and are erased on authentication or closure.

Parked test: ignition on, engine off; keep Y-Connect and nRF Connect disconnected. Connect once and record the exact phase. If it succeeds, request one snapshot and confirm fresh values before claiming live telemetry. If it fails, save the private diagnostic log and stop the connection. No engine-running step is required. Raw captures and credentials remain private.
