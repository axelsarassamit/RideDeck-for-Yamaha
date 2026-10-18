# Yamaha 0.12.51 navigation and fuel estimate

U-turn guidance normalizes provider casing and alternate maneuver names, including canonical TomTom MAKE_UTURN and TRY_MAKE_UTURN. The old exact MAKE_U_TURN match missed those real values. Visual U-turn indicators and English advance/immediate instructions share the same signs. Spoken instructions say make a U-turn without guessing a side for generic provider maneuvers. Along-route selection retains the cue briefly at the turn point instead of skipping it there.

Favorites preserve existing pins, Home and Work. Save your own name or the place name, choose a preset or custom category, browse groups, rename, recategorize or delete while parked.

Road preferences are accessible in Navigation and panels, the map menu and Roads in route preview. Motorways, tolls, ferries and dirt roads use documented provider options. Shortest distance and fewer U-turns rank returned alternatives. The U-turn preference adds 90 seconds per U-turn to ranking cost; it cannot forbid necessary turns. Valhalla avoidance is soft. Neither provider exposes a general avoid-all-bridges switch. Preferences do not grant access to restricted roads. Changes apply to new requests and reroutes.

Lane guidance requests Valhalla turn_lanes and TomTom LANES sections. Provider-recommended lanes are highlighted in left-to-right order on the phone and bike. Directions without recommendations never become guessed lanes. English lane prompts yield to turn instructions. Missing coverage displays no lane diagram. A TomTom extended-guidance rejection retries standard routing and suspends extended requests for this process. This does not sense the rider's current lane.

Fuel is a manual estimate using a confirmed full tank, fresh authenticated same-bike odometer, measured km/L and chosen refill buffer. Thailand XMAX 2024 nominal tank capacity is 13 L and remains editable. Full-to-full records can update measured consumption, including recorded partial fills. The map badge shows rounded estimated distance before the chosen buffer. It is not a measured gauge. Stale readings hide it; changed bikes, rollback and implausible odometer/model values invalidate it. Unknown CCU injection units are never converted to litres. Unrecorded refills and changing riding conditions affect accuracy. Setup requires parked, fresh speed and odometer readings.

## Parked checks

Verify favorites survive reopening, old pins remain and custom names/categories work. Inspect synthetic left/right U-turn and lane diagrams and English prompts. Compare the fresh odometer before recording a genuine full tank. Do not enter a fake production refill for testing. On a later ride, compare real guidance with signs and legal road access while operating the phone only when stopped.

## Sources

- https://www.yamaha-motor.co.th/commuter/xmax-tech-max-2024/specification
- https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route
- https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/guidance-instructions
- https://github.com/valhalla/valhalla/blob/master/docs/docs/api/route/api-reference.md
