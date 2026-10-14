# Yamaha 0.12.36: parked-bike check

Wait for this chat to confirm installation and indoor checks before going to the bike. Bring the computer, unlocked phone, USB data cable and bike key. Use this same chat.

## First connection

1. Park safely. Turn ignition on; leave the engine off. Connect the phone by USB and open RideDeck for Yamaha.
2. Close Y-Connect and disconnect nRF Connect. Keep the existing bike pairing. Do not forget or re-pair anything.
3. Open **Setup > Navigation > Bike data and fuel range**. The Setup navigation heading may include panels.
4. Leave **Keep bike data connected during a ride** off for this first check. Choose **Normal** under **Data refresh rate**.
5. Tap **Connect to parked Yamaha** and choose the paired Yamaha CCU. Tell the chat the connection message. It must authenticate independently of Y-Connect.
6. Wait for live values. Compare odometer, battery voltage, engine RPM and temperature with the bike display or the separately recorded Y-Connect reference. Tell the chat which values appear and their numbers. Engine-off RPM should be zero, not a previous reading.
7. Tap **Read bike snapshot** once. Do not repeatedly press it while a request is running. Report any error or missing values.

Do not start the engine until instructed. A brief stationary idle check is only needed if engine-off readings pass and conditions are suitable. Do not rev the engine for this test.

## Controls, navigation and logging

The chat will direct one control at a time. **Mark a parked test action** records what you observed. It does not establish that RideDeck decoded that brake or indicator. Keep the brakes released and indicators off between steps. The private log retains approved raw fields without authentication credentials or VIN.

Test the bike map separately: press zoom in three times, zoom out three times, then zoom in once more. Every press should receive feedback and change the map within its limits. Also check **Default view**, **Turn-by-turn**, **Turn list** and **Stop navigation**. A phone map check cannot prove the bike's button events.

If the first independent connection passes, optionally enable **Keep bike data connected during a ride**, reconnect and start a ride from **Ride book**. CCU values and raw bike fields join that ride's records. GPS speed, acceleration, deceleration and calibrated phone lean remain separately labeled estimates. No actual ride is needed to finish the first connection check.

## Finish

1. Tap **Stop bike data**. Check that the connection stops and values say **Last known** rather than appearing live.
2. When requested, use **Save private test log**. Keep it on the computer or phone; do not publish it.
3. Turn ignition off and leave indicators/hazards off.
4. Restore temporary Bluetooth HCI logging and Developer options settings after the last capture. Stay awake was originally off. RideDeck keeps its own visible pages awake, and the user-started data service retains a wake lock while connected.

## What still needs evidence

The decoder uses the source-confirmed server-1248 XMAX layout and existing captures. The next test must prove independent authentication, fresh values, disconnect behavior and actual bike-button handling. Fuel-injection counters are raw counts until their unit and reset behavior are established. They are not a fuel gauge. Remaining fuel and time to empty cannot be reliable without a confirmed fuel model. Brake/indicator bit assignments remain unverified. No actuator commands, fault resets or pairing resets are exposed.
