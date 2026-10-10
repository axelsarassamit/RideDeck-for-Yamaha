# RideDeck for Yamaha 0.12.24

Destination search now includes named shops, cafés and other points of interest alongside addresses. Google Maps shares resolve supported short links, place pins, search URLs and directions destinations. Camera positions are never substituted for a destination.

Before navigation starts, a large interactive map shows up to three actual motorcycle routes from Valhalla. A compact route row shows each route's estimated duration and distance. Riders can select a route, zoom, return to the overview and explicitly start the selected geometry. Planning or cancelling leaves the running route alone. A changed GPS start position triggers a fresh comparison.

Every destination triggers a nearby parking lookup. Recorded motorcycle parking and motorcycle spaces rank ahead of general parking; distance, recorded public access and fees help rank candidates. Private, prohibited and conditional-access records are excluded. General parking is explicitly marked when motorcycle access is unconfirmed. Riders choose whether to route to parking or directly to the destination. Distances to the destination are approximate straight-line distances. Capacity, present availability, legality and opening status are not inferred. Parking uses OpenStreetMap via the public Overpass service, caches results for five minutes and remains optional if unavailable.

Navigation, message reading and speech dictation default to English. Phone and bike map zoom now use the selected zoom level during navigation and discard outdated frames immediately. Phone map controls have larger touch targets.

Validation: 88 unit tests, including destination-vs-camera parsing, untrusted shared links, invalid coordinates, zoom persistence and parking filtering/ranking. Live provider checks returned Bangkok shops/cafés and three public Berlin motorcycle routes. Phone checks and signed-build verification are recorded separately. Physical bike button operation and road/glove tests require the bike.

Provider references: [MapTiler search](https://docs.maptiler.com/cloud/api/geocoding/), [Valhalla routes](https://valhalla.github.io/valhalla/api/route/api-reference/), [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started), [OSM motorcycle parking](https://wiki.openstreetmap.org/wiki/Motorcycle_parking).
