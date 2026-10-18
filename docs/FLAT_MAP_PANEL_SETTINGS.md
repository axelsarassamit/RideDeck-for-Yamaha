# Flat Yamaha map and panel settings

Setup > Yamaha > Bike display & maps now embeds the settings directly. The extra Map + Panel settings button, Navigation and panels menu and nested account-options dialog are removed.

- Panels shows the selected phone panel in one chooser. Bike view selection remains explicitly controlled on the bike; its map preview is a direct action.
- Guidance shows voice and lane-arrow switches, with a direct English voice test.
- Routing & traffic exposes the existing TomTom routing, refresh and incident preferences directly. Road preferences opens one editor and updates the visible avoidance summary without leaving or recreating the settings page. Location permission status updates after its permission result.
- Ride tools retain the prior actions. Fun ride appears when its activity is registered, so combining the feature commit preserves that entry without introducing a build-time dependency on work in progress.
- Advanced is one inline disclosure for account keys and Fuel Prices entitlement. Stored keys are never prefilled or logged. Blank fields retain current encrypted keys; typed keys are excluded from activity view-state saving.

NavigationUi.configure remains the compatible entry point for shortcuts and opens MapPanelSettingsActivity, which uses the same content as Setup. Existing preference names, routing semantics, accounts, bike protocols and app identity are unchanged.

Validation: 230 unit tests, debug app and instrumentation builds, UTF-8 checks, and MapPanelSettingsTest on an isolated Android 15 emulator. The test checks direct category access, setting persistence, recreation, the one Advanced disclosure, and disabled state-saving for key fields. No account keys or physical bike functions are exercised by this test.
