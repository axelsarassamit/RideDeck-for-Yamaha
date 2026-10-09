# RideDeck for Yamaha 0.12.20

- Reply method and Color theme now open their selectors, save changes and show the current choice in Customization.
- Phone maps use one tappable MapTiler/OpenStreetMap attribution row. Redundant navigation status and embedded phone credits are removed. Guidance stays at the top of the map. Bike frames retain their guidance and attribution.
- The header cycles Time, Time remaining, Arrival time, Distance remaining once each, every five seconds. Duration uses minutes or hours and minutes; distance uses one decimal and km units. Unavailable route values are skipped without repeating the Time slot.
- Map credits and About identify the active Valhalla/FOSSGIS route provider. Music access describes the selected player.
- Destination search can save a result as Favorite, Home or Work. Home/Work reject invalid coordinates without losing entered values. Manual favorites explain that coordinates are required.
- Precise location gives feedback when already allowed; Voice guidance includes a Close action. Placement details show the saved selection. Invalid saved appearance indices cannot crash the menu.

Install over the existing Yamaha app using the original signing identity. Physical bike commands and Yamaha accessory authentication require testing at the bike.
