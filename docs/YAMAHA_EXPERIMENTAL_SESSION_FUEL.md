# Experimental session fuel diagnostics

The regional MyRide research identified an application-side 1 Hz fuel-sample model. That is useful evidence for further investigation, but it does not establish a measured tank level or guarantee complete sample delivery on this XMAX.

The parked bike-data page now provides an explicit, non-persistent start/clear diagnostic. Starting requires an authenticated connection, the selected bike, and fresh parked speed and odometer. It reads only CAN 574 already requested by the existing read plan. No new CCU commands or interval changes are introduced.

The diagnostic shows sample count and covered time. It invalidates the session on a timing gap, a burst that might be duplicate delivery, malformed data, a changed bike, inconsistent/stale odometer, or disconnect. Repeated equal values at a regular cadence are legitimate samples and are not discarded. Reconnection starts with the experiment off.

The receive envelope currently does not expose a firmware sample identity to the reading consumer. An on-time retransmission cannot be distinguished from a new equal sample. Therefore this production integration deliberately keeps litres and km/L unavailable, even when receive timing looks regular. The pure model can calculate source-inferred totals only when its caller explicitly declares delivery verified; the production store never enables that declaration.

This diagnostic never subtracts fuel from the tank estimate and never changes manually recorded refills, range, or ride records. Source-inferred units require a controlled pump/refill comparison and a verified sampling/delivery contract before physical totals should be enabled.

## Checks

- Start while parked with fresh speed and odometer. Confirm sample count grows roughly once per second.
- Stop bike data. Confirm the experiment reports invalid/unavailable, rather than preserving a live fuel total.
- Reconnect. Confirm the experiment is off until explicitly started again.
- Keep the current manual full-tank range estimate unchanged throughout.

Pure model tests cover repeated equal values, duplicate bursts, missing samples, other-bike/odometer rollback, stale odometer, truncated samples, idle zero fuel, explicit invalidation and the default prohibition on unverified physical totals.
