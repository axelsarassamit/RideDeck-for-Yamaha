# Yamaha connection test 0.12.45

The parked XMAX 2024 test reproduced a timeout before the Bluetooth LE connection completed. Official Y-Connect could connect on the same bike and phone. Authentication and live RideDeck telemetry were not reached.

This candidate discovers the selected paired bike through a current BLE scan before calling connectGatt on the discovered device instance. It does not connect to an arbitrary nearby Yamaha. Discovery stops on selection, cancellation, failure or timeout. Discovery and connection have separate bounded waits. Missing discovery permission produces a recoverable message, including when a saved ride setting starts the service.

The data page requests Nearby devices discovery permission on Android 12 and later, or location permission on older Android versions. Pairing, navigation projection and the existing authentication protocol are preserved.

## Parked verification still required

1. Keep ignition on and engine off. Disconnect Y-Connect and nRF Connect, preserving pairing.
2. Open Bike data and fuel range. Allow the requested discovery permission.
3. Connect once. Record whether discovery finds the selected bike, whether GATT opens, and whether authentication succeeds. Do not equate discovery with authentication.
4. If connected, request one snapshot and compare fresh readings with a reference. Stop data and confirm it disconnects.
5. If it fails, preserve the exact phase and private diagnostic log before retrying.

Local compilation and existing decoder/unit checks do not establish that this change fixes the live connection. Publish as a test candidate until the parked verification succeeds. Raw captures and authentication material are excluded from the repository and release.
