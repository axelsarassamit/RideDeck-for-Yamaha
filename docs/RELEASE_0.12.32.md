# RideDeck for Yamaha 0.12.32

The cockpit now has separate adjacent Ride timer/Pause and optional Dash tiles using the compact rounded charcoal/green design. The pair mirrors with the other controls when their side changes. The ride timer uses the existing Yamaha ride session. Dash is hidden when the signed companion is absent; the ride timer remains available.

The native Dash control screen uses the shared DashRideControls component from Standard RideDeck, with Yamaha's existing session, permissions, visible gesture and recording-token logic behind a host adapter. Dash settings opens the companion only on that explicit settings action.

Status and preview queries no longer stay permanently busy after a timeout or screen pause. Each request has a distinct reply Messenger and query window, so an expired or cancelled reply cannot complete a newer request. Trusted fresh preview responses now confirm the actual camera state for REC as well as status responses. Camera samples are ordered across both query streams so a stale status result or timeout cannot erase a newer preview confirmation. Missing, invalid or expired camera state never claims recording.

The one-use signed recording token and sender privileges from 0.12.31 are retained. Recording is not inferred from a lease, timer or start acknowledgement. Physical phone checks for the final shared dock/layout, recording, timeout recovery, screen return and camera preview remain required after signing.
