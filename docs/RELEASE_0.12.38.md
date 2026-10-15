# Yamaha 0.12.38

Navigation now gives advance, approach and immediate turn prompts, rather than one announcement inside 200 metres. Timing adapts to current GPS speed: advance warnings are approximately 30 seconds ahead (300 to 1,000 metres), approach reminders 12 seconds ahead (100 to 400 metres), and immediate instructions 3 seconds ahead (20 to 80 metres).

Starting navigation gives the first instruction. Long uninterrupted stretches receive a reminder after at least three minutes and one kilometre of forward progress. Closely spaced turns include a short next-turn preview when within 120 metres. Arrival, confirmed off-route recalculation and loss of a previously acquired GPS signal are announced once per event. GPS jumps do not replay already spoken stages; late fixes skip obsolete warnings. Muting navigation still disables all prompts, and the existing English-only voice selection remains in place.

Automatic traffic refresh does not repeat the route-start announcement. Bike authentication/data and Dash recording behavior are unchanged. Acceptance requires scheduler/speech unit tests, encoding/build checks and verification of the installed build. Actual announcement timing and audibility on a ride still require a physical route test.
