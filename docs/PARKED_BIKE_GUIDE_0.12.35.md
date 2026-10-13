# Yamaha / Y-Connect connectivity: parked-bike manual

Updated 14 October 2026. The target is every Y-Connect function supported by this XMAX in **RideDeck for Yamaha**. Fuel data is one part of that target. The original Y-Connect app can be used as a reference during an agreed capture session.

## Next short test: RideDeck independent connection

Wait for the chat to confirm the signed 0.12.35 test update is installed before going downstairs. Bring the laptop and USB cable. Park, turn ignition on and keep the engine off.

1. Leave the phone unlocked, connected by USB and on RideDeck. RideDeck keeps its test screen awake. Confirm the installed version with the chat.
2. Close Y-Connect and disconnect nRF Connect. Do not unlink, forget or re-pair the bike.
3. Open **Setup > Navigation and panels > Bike data and fuel range > Connect to parked Yamaha**. Select the paired Yamaha CCU.
4. Read the connection result to the chat. The test first requests connection details from the paired Yamaha, then tries the accessory authentication and asks which observed vehicle fields are supported.
5. When supported IDs appear, press **Read bike snapshot** once. Tell the chat the packet count and displayed IDs. Do not start the engine or change physical controls until instructed.
6. Use **Mark a test action** only for a state you can see. Make one change at a time when the chat directs it. The label records your observation, not a decoded bike signal.
7. Press **Stop bike-data test**. Use **Save private test log** only when requested and keep the file private. Authentication packets and pairing codes are excluded from this log.
8. Test the bike map's zoom separately: three zoom-in presses, three zoom-out presses and then one more zoom-in press. Report whether each press changes the map. Default view, Turn-by-turn and Turn list can then be checked separately.
9. Turn ignition off when the chat confirms the checks are finished. Restore any temporary Developer options settings after the final capture. The original Stay awake setting before the diagnostic session was off.

The independent connection and repeated zoom are software-ready tests, not hardware-verified results. Brake/indicator decoding and bike fuel measurements remain unverified. No factory settings, fault resets or actuator commands are exposed by the test page.

## Before going to the bike

Bring the laptop, charged phone, USB data cable and bike key. Open this same **RideDeck Yamaha** chat on the laptop.

Before the update is installed, Yamaha 0.12.34 is the last verified installed version. The chat must check the installed version before directing you to the new connection test.

## Connect and resume

1. Park the XMAX 2024 safely. Turn ignition on and leave the engine off initially.
2. Connect the unlocked phone to the laptop with the USB data cable. Allow USB debugging for this computer if Android asks.
3. Turn phone Bluetooth on. Keep the existing Yamaha CCU pairing. Open **RideDeck for Yamaha** and leave it visible.
4. In this same chat, send:

   **Resume only Yamaha/Y-Connect connectivity and bike-data diagnostics. I am at the parked XMAX 2024, ignition on, phone unlocked, USB connected, and RideDeck for Yamaha open. Check the USB connection and installed version, then investigate accessory authentication and supported bike data. Keep all other development and Dash video analysis paused.**

5. Say which computer has the USB cable: Windows or **Axel-macbook**. The phone and laptop can use different networks; USB is the local debugging link. If using the Mac, the chat must first confirm host access and transfer any required saved diagnostic files from Windows.
6. Leave the phone untouched during each check. Make one requested change at a time.

## Observe the working Y-Connect connection first

Running Y-Connect on your paired phone can help identify the connection, authentication and data exchanges. Start this session with the existing Y-Connect app; it does not require the prepared RideDeck update.

1. Before reconnecting the bike, note the current **Bluetooth HCI snoop log** setting in Android Developer options. Enable full logging if the phone offers it, then turn Bluetooth off and on for the setting to take effect. Keep the bike parked.
2. Pause RideDeck's bike connection for the reference capture so both apps are not trying to control the same accessory connection. Keep existing pairing and app data.
3. Open **Y-Connect** and connect the XMAX. Tell the chat when you tap Connect and when the app shows Connected. Capture the initial exchange first.
4. Exercise one feature at a time using the checklist below. Note its time and visible result. Use sample notifications and local music. Do not send messages or place calls merely for this test; call handling can be checked separately when an incoming test call is arranged.
5. The computer will collect an Android bug report and check whether it includes usable Bluetooth traffic. Some phone builds omit or filter payloads, so a short trial capture must be checked before a longer session. App-level encryption can still limit decoding.
6. Keep the capture local because full logs can contain private information. Restore the original HCI logging setting after capture. Disconnect Y-Connect before the separate RideDeck connection test.

These are observations for an independent RideDeck implementation. A capture is evidence of behaviour, not proof that a function has already been implemented.

### Complete function checklist

For each function record: **available on this XMAX, action/time, visible result, exchange identified, RideDeck implementation and bike test result**. Mark unavailable or unverified explicitly.

| Function group | Reference check |
|---|---|
| Connection | Pairing/authentication, initial data, supported fields, disconnect, reconnect and ignition cycle when requested |
| Fuel | Consumption views, cumulative fuel use, available level/range fields, units and update timing |
| Revs Dashboard and vehicle data | Every value actually displayed, including RPM or other engine readings where available |
| Maintenance | Oil and other available reminders, mileage/days and existing settings without resetting them |
| Malfunctions | Existing notices/codes and status; do not create faults to test this |
| Ride history | Available trip, distance, route and recorded data views |
| Parking | Last parked location and the event that updates it |
| Phone status | Battery/charging, signal, app/Bluetooth connection and available headset indicators |
| Notifications and calls | Message indicators/history, caller display and supported handlebar actions |
| Music | Native bike player metadata, play/pause, previous/next and volume; keep RideDeck music controls on the phone as previously requested |
| Clock and weather | Time sync, current conditions and available forecast views |
| Other features shown on this bike | Inventory every remaining menu and action rather than assuming features from another Yamaha model |
| Yamaha account/cloud features | Record available rankings, historical records and account-dependent features; Bluetooth capture alone cannot reproduce Yamaha server access |

Yamaha states that feature availability varies by model. The checklist is the investigation scope, not a compatibility claim for every row. Source: https://global.yamaha-motor.com/business/mc/connectivity/yconnect/iceg/

Android documents HCI logging and bug-report extraction here: https://source.android.com/docs/core/connect/bluetooth/verifying_debugging

## Separate RideDeck connection inspection

After the chat verifies and installs the required signed update, open:

**Navigation and panels > Bike data and fuel range > Connect to parked Yamaha**

Choose the Yamaha CCU, not a headset. Tell the chat exactly what the result says.

This test checks the observed Bluetooth LE service, attempts authentication and requests raw supported vehicle fields. It closes when you leave its screen or after ten minutes. It does not yet decode fuel values. The existing navigation connection is a separate channel; a working map does not prove the accessory data connection works.

## What we need to verify

1. The correct Yamaha accessory service and a stable connection.
2. The authentication/pairing exchange needed by this CCU.
3. The supported vehicle-data fields for this specific bike.
4. Actual readings, their units, timestamps and update rates.
5. Reconnection and counter behaviour after ignition off/on, checked only when requested.

Priority readings are **fuel consumption, cumulative fuel used and fuel level/remaining fuel**, where the bike exposes them. Also identify other supported data, such as odometer, wheel speed, engine readings, ignition and malfunction information. These are investigation targets, not a claim that every field is already available to RideDeck.

## Dashboard reference

While parked, note or photograph the dashboard's fuel bars, odometer, trip distance, average consumption, instantaneous consumption and range, where shown. Keep the exact units, especially **km/L versus L/100 km**.

Do not reset trips, refuel, change pairing or switch ignition during a reading check unless asked. Keep the engine off until a specific check needs it running. Y-Connect is used for the reference capture, then disconnected for the RideDeck comparison.

## Fuel-range target

Verified bike fuel data will be used for **estimated distance and riding time until empty**. A cumulative fuel-use counter alone does not tell us how much remains. Gauge bars need a verified relationship to usable volume. Old, missing or unknown-unit readings must remain unavailable.

Validate estimates against normal rides and refill records. Do not deliberately run the tank empty.

## If the connection fails

Tell the chat the exact screen message and whether Bluetooth, USB and ignition are still on. Keep RideDeck visible and use its bike diagnostic report when requested. Keep keys, authentication material and raw personal logs private. Preserve the current app data and pairing while the failure is investigated.

Yamaha app development has resumed with all pending Yamaha changes. Dash development and video analysis remain paused. This manual covers Yamaha/Y-Connect connectivity and verification of its supported functions.
