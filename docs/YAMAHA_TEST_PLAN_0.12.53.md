# Yamaha 0.12.53 checks

## Indoors before the bike

- Setup > Yamaha > Bike display & maps shows current panel, voice, lane and traffic choices directly. The old nested chooser is gone. Existing account keys remain encrypted and hidden.
- Go for a fun ride: choose start, riding time, finish, style, direction, stops and road preferences. Compare actual road routes and durations on the large map. Cancel and rotate without replacing a running route. Unavailable routes must not appear as invented roads.
- Winding routing requires TomTom. Relaxed Valhalla routing has no live traffic. A requested duration is a target, and stop time is extra. Coordinates do not guarantee scenery or motorcycle access.
- Existing favorites, Home, Work, panel choices, keys and pairing survive the signed update.

## Parked bike, ignition on and engine off

- Let the saved map and data connection start. Confirm fresh odometer and speed. Close Y-Connect and disconnect nRF Connect while RideDeck owns the CCU.
- Check map readability, repeated zoom, native view switching and the new route overview. The shared settings page must still reach the Fun ride planner and bike diagnostics.
- Optional fuel sample test: start explicitly while parked, observe count and coverage, then save the private log. Stop/disconnect must invalidate it; reconnect starts with the test off. Litres and economy remain unavailable until delivery and pump calibration are validated. No additional idle engine or brake repetitions are requested.
- Manual tank-range estimation still requires a genuine full tank, measured consumption and chosen reserve buffer. Do not enter fake production refills for testing.
- Save the private test log, stop navigation/data/map sharing and turn ignition off. Keep USB connected for export.

## Later normal ride: U-turn verification remains pending

Use a familiar, legal Thai U-turn. Inspect the route while stopped. During a normal safe ride, check the U-turn symbol, early English announcement, approaching-turn reminder and retention until completion. Check a closely following maneuver when available. If a turn is naturally missed, check rerouting and remaining stops. Do not deliberately miss a turn, stage hard braking, or interact with the phone while moving. Review results and logs after stopping.

Lane coverage, motorcycle restrictions and current incidents depend on the routing provider. Indoor fixtures and compilation do not pass a physical road test. The research found a source-based LinkBox2 consumption model, not a tank-level gauge, brake-pressure decoder or validated meanings for IDs 3 and 9.

## Provider fixture provenance

`androidTest/assets/bangkok_public_loop.json` is a trimmed response from one real Valhalla motorcycle request through public Bangkok landmark coordinates, with three legs, 15.085 km and approximately 37.6 minutes. It contains no user location, account key, CCU capture or motorcycle identity. It validates multi-leg parser alignment; it is not a current traffic or road-access guarantee.
