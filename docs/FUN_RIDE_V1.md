# Fun ride contract v1

Both editions share the question meanings and ordered itinerary. Yamaha starts built-in navigation. Standard must show only verified external navigation apps and state whether the app preserves the exact geometry or recalculates between stops.

Request shape (all coordinates are WGS84 decimal degrees):

```json
{"version":1,"start":{"lat":13.5,"lon":100.5,"label":"Start"},"finish":{"lat":13.5,"lon":100.5,"label":"Return to start"},"targetMinutes":60,"style":"relaxed","directionDegrees":0,"stops":[],"avoid":{"motorways":true,"tolls":true,"ferries":false,"dirt":true,"fewerUTurns":false}}
```

- `targetMinutes`: 30 to 360; UI offers 30, 60, 90, 120, 180, 240, 360. Riding time excludes stop duration.
- `style`: `relaxed` or `winding`. Winding needs TomTom motorcycle thrilling routing; Valhalla cannot claim this preference.
- `stops`: zero to two explicitly selected real places, in user order. These replace exploration seed points. Final destination is always last. Return-to-start is represented by equal start and finish coordinates.
- `directionDegrees`: north 0, east 90, south 180, west 270. Exploration seeds are starting guesses; they do not identify scenery, cafes or road quality.
- Avoid flags use existing RoadPreferences semantics, provider availability and limitations. They must never be described as legal-access guarantees.

Yamaha implementation generates at most three candidate itineraries, each with two geodesic exploration seeds and a final destination. Each candidate is calculated by the real routing provider. Selected explicit stops make one itinerary request. Network calls are sequential with at least 1.2 seconds between starts. Cancellation prevents further requests and discards late results. Public Valhalla is fair-use and has no traffic. TomTom requests traffic available to the account, subject to coverage and allowance. No AI service is used.

Candidates need at least 1 km and actual routed duration between 40% and 200% of requested time; actual duration remains prominent. Up to three geometrically distinct routes are ranked by duration closeness and U-turn penalty. The target is not a guaranteed duration. Review the full map before starting. Coordinate guesses can fail to snap or be geographically unsuitable; failures are omitted, never shown as invented roads.

The existing full-size route map shows actual provider geometry and itinerary markers. Back/cancel does not change running navigation. Road changes require returning to the planner and regenerating. Start requires a fresh phone location within 100 m of the planned start.

Ordered remaining waypoints and style are saved locally with navigation resume state. Off-route recalculation and traffic refresh route through the unvisited stops. Stop navigation clears the itinerary. Intermediate arrival markers do not stop navigation. Finishing a stop uses routed progress, not a timer or straight-line proximity.

Limits: no automatic scenic-place discovery, weather scoring, road-condition assessment, booking, or fuel-stop guarantee. Motorcycle restrictions can be incomplete. No modifications to Yamaha authentication, bike packets, or CCU behavior.

References: https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route and https://valhalla.github.io/valhalla/api/route/api-reference/
