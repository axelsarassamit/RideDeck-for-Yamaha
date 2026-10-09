# Native Yamaha phone-status icons

Requested 2026-10-09 from Axel's photo: message bubble, cellular signal, phone battery, App connection and headset. Restore these in the bike's native status bar. The outdoor temperature is bike-owned and is not replaced with phone weather.

Status: unfinished. Local packet groundwork is implemented; native icons are not restored. Axel has lifted the release hold for the implemented 0.12.15 features. These native icons remain pending accessory connection and bike verification.

## Message icon follows RideDeck

Axel clarified that the native message bubble should sync with RideDeck's own message panel. `GX12NotificationListener.messageIndicator(context)` now exposes metadata-only state from the exact same `MessageInbox` and selected-app preferences that supply the phone panel. There is no second message queue or independent Android notification count.

- PENDING: at least one selected-app preview is still pending in RideDeck. The indicator stays on when Seen / next advances to another pending message.
- CLEAR: the RideDeck queue is empty. Acknowledging the final preview, clearing the inbox, or deselecting all remaining sources clears the state. During the current app process, an unchanged acknowledged notification does not relight it; new content on that notification can. Preview and acknowledgement history remain in memory as before.
- UNAVAILABLE: the notification listener is disconnected. Do not translate this to a verified empty inbox. The future accessory writer must handle lost notification access and reconnect explicitly.
- Android notification removal currently disables reply/read actions but retains the RideDeck preview until Seen / next. The bike must follow that same behavior, rather than clearing while the phone still displays a pending message.
- Merely opening the message reader or using Read aloud does not acknowledge a message. The existing Seen / next behavior is preserved.

This state contains no sender, title, body, app name or notification key. No message content is transmitted or persisted by the new API. The native wire update still requires a verified Yamaha accessory session and message-indicator/clearing protocol; the API alone does not restore the bike icon.

## Why the map can work while the icons are absent

The active navigation connection sends Garmin NaviLite frames and map images in the CCU's 480 x 234 navigation viewport. The photographed native header belongs to Yamaha accessory functionality outside that viewport. Drawing additional icons into a map JPEG does not control that header.

Yamaha's public documentation describes message notifications, Android cellular signal, remaining phone battery and its app connection indicator: https://global.yamaha-motor.com/business/mc/connectivity/yconnect/iceg/meter/

## Independently verified packet groundwork

Read-only analysis of the phone's previously exported Y-Connect 3.9.0 executable, SHA-256 `2f02f003e7006b439867e0dd89b64df44909ae4de9c43f3b03e9eb0d05e05434`. No original executable code or application credentials are included in this repository. The codec is our own small implementation of these wire layouts.

- SDK class `a` identifies BLE service `AFA2CDF4-ECCF-46A7-A5EA-9DA428C0157A`, control write characteristic `B606C7F9-E5A1-4E75-B313-2A920054A8EB`, and control notify characteristic `9C810D26-B605-4306-8C1C-755A1BA3066C`.
- `d$w.a()` writes a two-byte command ID followed directly by its payload to the control write characteristic. `e.a(Number[])` uses big-endian byte order. These are not NaviLite frames; do not add the navigation framing, CRC, request-ID byte or little-endian command ID.
- Command catalogue `c`: PhoneBattery `0x0115`, BluetoothHeadsetStateResponse `0x011C`, PhoneCellSignalLevelResponse `0x0133`.
- `d$c`: battery percentage byte, then charging boolean byte. `d$f`: connected boolean byte.
- `d$l`, `e.a(McPhoneCellSignalLevelResponse)` and `e$a`: NoConnection=0, NoSignal=1, Lv1=2, Lv2=3, Lv3=4, Lv4=5. These differ from Android signal levels.
- Native App connection is established by the real accessory session, not an invented outgoing icon command or the map-connected flag.
- Notifications use separate category and data protocols. A safe message-icon update has not yet been established for this CCU. No message contents are transmitted by the new code.

Additional static inspection: `McNotificationDataV2` has notification ID, category, date/time, title, subtitle and detail fields. It is not a simple unread boolean. Do not pass RideDeck preview text to that API or assume an empty notification toggles the header icon. A matching indicator/clear sequence must be verified first.

`YamahaAccessoryStatus` encodes only the three confirmed status messages. Missing, malformed or unavailable readings are omitted. It is deliberately not wired to the Bluetooth transport until a matching authenticated accessory session is verified.

## Remaining work before this item is complete

1. Identify the bike's actual accessory BLE endpoint and exposed characteristics while parked. Confirm whether it is distinct from the selected Garmin navigation device. Keep existing bonds and phone settings.
2. Verify accessory authentication and status notification behavior. The multi-model SDK also contains SPP-assisted BLE connection information, CCU identity and pass-key based AuthenticationV2; their presence alone does not establish the handshake this XMAX uses. Do not invent credentials or send guessed authentication messages.
3. Implement the matching accessory session with serialized GATT operations, acknowledgements, disconnect clearing, retry and explicit Stop. Preserve the existing navigation connection.
4. Feed real Android battery/charging, cellular signal and Bluetooth audio profile state. Missing phone permission and a disconnected headset must remain distinguishable from an unknown reading. Determine the required signal permission against Android's actual API behavior before adding it.
5. Verify the message-indicator protocol and native clearing behavior using selected notification apps. Preserve the current notification privacy policy, which keeps previews on the phone.
6. Test all five icons on the bike, including charging, headset disconnect, signal loss, dismissed messages and accessory reconnect. App connection must reflect the accessory session independently from map streaming.

No bike write, new pairing, phone permission change, app installation or remote release was performed during this groundwork.
