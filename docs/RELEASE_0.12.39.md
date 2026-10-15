# RideDeck for Yamaha 0.12.39

Navigation now prefers a fresh satellite fix over a less accurate network position, rejects stale/future/out-of-order fixes and implausible short-interval jumps, and requests unbatched high-accuracy GPS updates every 500 ms on Android 12+. Hardware may deliver a slower rate. Reacquisition after a gap remains possible. Reliable speed and bearing checks remove uncertain measurements from guidance/camera decisions without inventing GPS samples. The phone shows source, reported accuracy and available GPS speed; old speed is hidden after five seconds and guidance treats ten-second-old fixes as lost. Ride-book raw measurements are unchanged.

The phone has a separate large instruction header and a 64 dp control rail. Zoom buttons cannot cover map pixels or the instruction. Route overview controls sit below its map. The bike frame puts maneuver, distance and arrival on the right, clear of the firmware's left-side zoom overlay; the current road, one speed sign and map credits remain visible. Firmware overlay placement still needs confirmation on the actual XMAX.

Route choices have consistent map/card colours, fastest-first ordering, a shorter-distance label when applicable, and extra travel-time comparisons. Routes with nearly identical geometry and distance are merged even if providers encode different vertex densities. Truly different or longer loop routes remain available. TomTom explicitly uses fastest traffic-aware motorcycle routes, avoids unpaved roads as well as motorways and tolls, and receives heading only when fresh, moving and reliable. The Valhalla fallback now requests minimum motorway/toll preference; those preferences are not a legal-access guarantee. Always follow local signs.

## Validation

187 unit tests passed locally, including recorded numeric scenarios for fix quality and route shapes. No fake GPS samples were sent to the installed app, bike or ride book. Signed phone checks cover real Android layout bounds, both bike image heights with a simulated firmware overlay, and a live TomTom request between public Bangkok locations. Check the final release verification for actual phone results. No live outdoor precision or moving-bike performance is claimed from indoor tests.

## Parked and road check

1. While parked outdoors, open the map and wait for GPS rather than Network. Check the reported accuracy, then press zoom repeatedly in both directions. On the bike, confirm the firmware controls leave the right-side turn text clear.
2. Search a familiar destination. Compare route colours, road geometry, time and distance in overview before starting. Confirm the selected route is the one followed.
3. During a normal ride, assess turn timing, position tracking and recovery after a tunnel or covered area. A passenger may note issues; do not operate the phone while moving. Stop safely before reporting a timestamp and location privately.

Provider references: https://developer.android.com/reference/android/location/LocationRequest , https://developer.android.com/reference/android/location/Location , https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route , https://github.com/valhalla/valhalla/blob/master/docs/docs/api/route/api-reference.md
