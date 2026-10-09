# XMAX 2024 function inventory

Investigated 2026-10-09. Scope: RideDeck for Yamaha only. User identifies the bike as XMAX 2024. Exact regional trim and CCU software revision still need confirmation. The live RideDeck session reported a 480 x 234 navigation viewport.

## Evidence and limits

- Inspected the existing Yamaha checkout, including YamahaCastService, NativeNavigation, NavigationUi, DashContentCommand and Handshake.
- Read the connected phone's installed executable packages without launching the original apps: Y-Connect 3.9.0 (`jp.co.yamahamotor.yamahamotorcycleconnect.sccu`) and Garmin StreetCross 1.87 (`com.garmin.android.apps.streetcross`). No private account data was accessed.
- Extracted DEX class, field and method identifiers for interoperability. These may be definitions or references. Their presence establishes a program capability, not that this particular CCU supports it or that its wire format is understood.
- Y-Connect's McClient contains 75 distinct write-method identifiers. The companion JSON records that catalogue, its receive listeners, and StreetCross message-method identifiers. Firmware, factory and settings methods are discovery records only.
- Live RideDeck diagnostics showed authentication, acknowledged image streaming, a calculated route, image start/stop requests and empty favourite/station lists. A running service alone does not prove every feature works.
- Public NaviLite documentation is independent reverse engineering, not an official Yamaha protocol specification. Some layouts and enum meanings remain inferred. Latest upstream descriptions include details absent from RideDeck's older implementation.
- Yamaha's regional XMAX manual is useful for the display family, but the currently served BKA-F8199-U1 PDF is a later revision. Do not call it the exact manual shipped with this user's 2024 bike.

## Garmin navigation functions

| Function | RideDeck for Yamaha now | Missing work or evidence |
|---|---|---|
| Connect, authenticate and identify navigation viewport | Implemented; live authentication and 480 x 234 stream observed | Log the CCU model family without recording nonce or device identity |
| Map view with route | Implemented; live image acknowledgements observed | Verify bike display appearance and physical view changes |
| Address/place destination search | MapTiler implementation | Different search service from Garmin; provider availability applies |
| Route calculation | Valhalla motorcycle route implementation | Online provider; no Garmin offline-map parity |
| Route progress and route active status | Phone/map content exists; start/stop status writes exist | Native dashboard status must follow route lifecycle, not merely image requests |
| Native Turn-by-turn view | Native service 4, current road and GPS/route status added in 0.12.12 source | Physical bike verification pending |
| Native Turn List | Content type 2, metadata 5, rows 97 and active index 6 added in 0.12.12 source | Physical bike verification pending |
| Change view with bike buttons | Services 55/56 selectors decoded and logged; native updates continue with image stream paused | Verify all three modes and return to Map |
| List scrolling | Bike renders native list when populated | Supply list contents and active index; do not reinterpret scroll controls as map zoom |
| Next-turn distance, arrow and instruction | Drawn into map/arrow images | Also transmit native guidance data; translate routing signs into Garmin icon ordinals |
| Current road | Empty setup value | Send actual road when route provider supplies it |
| ETA, remaining distance and duration | Some information drawn into map content | Native ETA service 1 and correct lifecycle updates missing |
| Voice directions | Phone text-to-speech implementation | Test audio routing with actual headset and current route |
| Map zoom from bike | Services 51/52 handled | Zoom label/status response is setup-only; test live direction and acknowledgement |
| Stop navigation | Service 49 handled | Verify full dashboard state reset, turn-list clearing and route-status update |
| Home and Work | Saved places and services 53/54 implemented for new route | Native bike verification; next/final-stop options absent |
| Favourites | List metadata/rows and new-route selection implemented | Live test returned zero items; verify populated list and native row schema |
| Nearby fuel stations | Cached list/rows implemented | Fresh phone search and populated bike list still need verification |
| Add destination as next/final stop | Rejected with an explicit unsupported message | Multi-stop route model and route options 2/3 missing |
| Skip next stop | Service 50 not handled | Requires multi-stop route support |
| Speed limit and speed-related guidance | Static unknown limit at setup | Reliable limit data and live service 17 updates missing |
| Lane guidance | Not implemented | Richer service 19 applies to a different CCU family; first identify the model |
| Day/night appearance | Fixed setup value | Dynamic mode and bike response verification missing |
| Traffic, rerouting, camera, border and school alerts | Not implemented | Region/provider-dependent data and native event updates/dialogs |
| Toll/route-choice prompts and bike responses | Not implemented | Verified dialog layout, IDs, callback handling and timeout behavior |
| Bike speed feedback | Service 65 ignored and suppressed in diagnostics | Unit/schema verification; navigation input only, not engine diagnostics |
| Offline maps, map download/update and saved route objects | Not implemented | Separate product capability from image transport |
| Connection loss, retry and image pause/resume | Partial watchdog/receiver handling | Verify recovery for each display mode without losing route |

## Yamaha accessory and phone functions

These belong to Yamaha's accessory functionality. RideDeck's current navigation-image connection does not provide these native dashboard screens. A JPEG labelled Music is a different feature from the bike's native audio player.

| Function | Installed Yamaha SDK evidence | RideDeck for Yamaha now |
|---|---|---|
| Accessory connection and authentication | McClient connection, SPP/BLE and authentication methods | No Yamaha accessory client |
| Music track information and progress | `writeBluetoothMusicMetaData`; song, artist, album, duration, elapsed time, rate, playback state fields | Phone controls retained; custom bike Music image removed; native dashboard transport absent |
| Music Play/Pause/Next/Previous | `addBluetoothMusicControlListener`; enum fields Play, Pause, NextTrack, PreviousTrack | Phone playback controls; native bike callbacks absent |
| Music/call volume and adjustable-state reporting | PhoneVolumeControl listener; volume-level and controllable-state writes | Native dashboard volume absent |
| Headset connected state | Headset-state request listener and response write | Android audio route checks; native bike status absent |
| Incoming call details and call state | IncomingCallInformation and CallChangeNotification writes | Native bike call state absent |
| Answer, reject or end call | IncomingCallControl listener | Native commands absent; Android permission/role compatibility needs investigation |
| Notification add/update/remove, categories and content | NotificationDataV2, Add/Update/RemoveNotification writes and removal listener | Phone message card exists; native bike history/notification channel absent |
| Phone battery and charging | PhoneBattery model has battery and charging fields | Available in local diagnostics; native bike updates absent |
| Phone cellular signal | Signal-level request listener and response write | Native bike updates absent |
| Phone connection, combined state and thermal state | PhoneStateCombined and PhoneThermalState writes | Native bike indicators absent |
| Clock/date synchronization | DatetimeChangeNotification and VehicleSettingDatetime writes | Phone clock only |
| Current/location weather | Weather request listener, location and information writes | Native weather absent |
| Forecast hours/days | Hour 1/2/3 and day 1 through 6 weather writes | Native forecast absent |
| Vehicle identification and supported data | VehicleIdentification and VehicleInformationSupportIdList requests | No accessory telemetry client |
| Vehicle information and ignition state | Information request/interval/subscribe methods and ignition listener | No verified live telemetry |
| Fuel use | CumulativeInjectionQuantity request/response | No fuel-use history |
| Maintenance reminders | Official Y-Connect feature | No bike-data-backed reminders |
| Malfunction/code information | Malfunction request/response/interval identifiers | No verified diagnostic fault reader |
| Last parking location | Official Y-Connect feature | Not implemented as a persistent parking record |
| Ride records, Revs Dashboard and rankings | Official Y-Connect features, model-dependent | No equivalent feature set or Yamaha cloud integration |

## Other SDK functions discovered

The SDK also contains image/photo-frame and push-notification graphics, virtual pit-board, lap/sector timing, YRC settings, vehicle CAN data requests, firmware update, factory mode and Wi-Fi-host functions. This is a multi-model SDK. Their presence does not establish XMAX 2024 compatibility. They are not queued as bike control actions. Yamaha-hosted account/ranking services also require their own authorized service integration.

## Correction to the earlier diagnosis

Services 55 and 56 carry a content selector: 1 for navigation images, 2 for Turn List, 3 for favourites and 4 for stations. The previous RideDeck implementation accepted only selector 1, with separate list handlers for 3 and 4. Selector 2 was ignored. Its diagnostics printed service/type/size/action but omitted that selector, so they cannot demonstrate that the bike never sent a view-selection request. Repeated image-stop commands can accompany leaving the map view.

The previous implementation also omitted native next-turn information and turn-list contents. Version 0.12.12 source now includes both, plus route-state clearing and image-stop acknowledgement. Packet fixtures pass locally; actual bike behavior remains unverified until the matching signed update is installed and exercised.

The layouts were checked against the installed StreetCross 1.87 ARM64 native packers. Service 5 uses POINTER data type 1, unlike an upstream documentation table. Service 4 packs icon, little-endian float distance, unit length, road length, unit and road. Service 97 packs little-endian index, icon, instruction length, unit length, float distance, unit and instruction. No original executable code is included in this repository.

## Implementation order

1. Decode bounded content selectors and log their names without raw payloads. Add XMAX native turn guidance and Turn List data, active-index updates and route-state clearing. Preserve the existing map frame transport.
2. Verify Map, Turn-by-turn and Turn List using the bike's actual Change view menu, including list scrolling and return to Map. Record the corresponding selectors.
3. Identify Yamaha accessory transport/authentication and obtain matching music metadata/button packet evidence. Then connect Android media sessions to native Yamaha music controls and volume.
4. Implement remaining accessory status, notifications, calls and weather against verified schemas; compare the bike behavior with the original applications one function at a time.
5. Add multi-stop routing and remaining Garmin actions. Keep region/model-dependent telemetry and cloud functions explicitly tracked.

## Sources

- Yamaha XMAX regional manual, display/menu and smartphone chapters: https://www.yamaha-motor.co.th/docs/owner-manual/commuter/th/bka-f8199-u1.pdf?Status=Master&sfvrsn=fa4253b2_2
- Yamaha dashboard feature descriptions: https://global.yamaha-motor.com/business/mc/connectivity/yconnect/iceg/meter/
- Yamaha navigation-enabled Y-Connect and StreetCross: https://www.yamaha-motor.co.jp/mc/lineup/y-connect/garminstreetcross/
- Yamaha broader Y-Connect capabilities, model-dependent: https://global.yamaha-motor.com/business/mc/connectivity/yconnect/yrc.html
- Pillion NaviLite interoperability observations: https://github.com/alexandrevega/pillion/blob/main/docs/PROTOCOL.md
- Local identifier inspection: Y-Connect 3.9.0 and StreetCross 1.87 installed on the authorized connected phone. See `XMAX_2024_FUNCTION_CATALOG.json`. No proprietary executable is copied into this repository.
