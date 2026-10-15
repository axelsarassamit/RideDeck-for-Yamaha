# RideDeck for Yamaha 0.12.41

Code maintenance release. No user features are intentionally removed and no settings migration or credential deletion is performed.

- Removed 29 unreachable private helpers from MainActivity, including obsolete Standard-edition app launching, adjacent split-screen, dock, destination and local chronometer controls. The active Dash ride-session timer remains.
- Removed external map-app selection branches and hidden map-size settings. The ten visible Yamaha customization actions keep their order and behaviour; shared destinations still open native route selection or search.
- Removed unused GraphHopper build/debug configuration, an unread routing-profile write, obsolete map-size diagnostics and twelve unnecessary external navigation package queries.
- Removed the obsolete DedicatedDisplay compatibility facade. Existing bike commands call the same native navigation functions directly; diagnostics now report actual navigation state instead of an always-ready helper. Bluetooth packet generation and authentication are unchanged.
- Deleted the superseded BikeMapRenderSpec and its three self-contained tests. The current BikeNavigationLayout tests remain. Y-Connect decoder and protocol fixtures are retained for ongoing bike-data work.
- Updated setup documentation and removed the signing helper's obsolete default version, requiring an explicit numeric version.

Validation: 184 remaining unit tests, UTF-8 checks and debug build. A connected-phone regression verifies the visible customization choices and reply-method selection, restoring the original preference afterward. Existing map/menu regression checks cover navigation and diagnostics. Phone results and exact signing provenance are verified before publication. Actual bike connectivity is not revalidated by this maintenance pass.
