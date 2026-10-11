# RideDeck for Yamaha 0.12.29

Starting or resuming a ride with **Record with Dash** enabled requests recording without opening the Dash app. Yamaha stays on screen and provides rear/front live images for checking the mount angle, with a large **Done** button. The previous saved automatic-start choice is preserved. **Dash settings** opens Dash only when the rider chooses it.

The explicit visible rider gesture first establishes the authenticated ride lease, then binds the signed Dash CompanionRecordingService with Android capability propagation. The app-owned binding is retained through the ride and released on Pause, End or service destruction. Pending connections cancel when the rider leaves the screen; lost connections never reconnect automatically. Heartbeats and background restarts cannot request recording.

If Yamaha's camera or microphone runtime grant is missing, the explicit Start/Resume action requests those permissions before starting. The rider taps Start/Resume again after granting them; the action is never replayed automatically behind Android's permission prompt. Both grants are checked again before binding and before sending the recording request.

Messenger version 1 uses what=1 for recording and what=2 for preview. Replies must come from Dash's verified package UID. Recording acknowledgement is described as a request; only the preview's recording field reports camera state. Permissions, power, storage and camera eligibility remain Dash's responsibility. Missing setup produces a visible explanation without switching apps.

Preview requests are limited to one outstanding request, while the angle screen is visible. JPEG frames are limited to 100 KB per camera, decoded with bounded dimensions, and rendered FIT_CENTER without cropping. Missing frames and images not refreshed within one second are cleared. Closing the preview stops polling and allows Dash's decoding lease to expire.

Reference: [Android capability propagation](https://developer.android.com/reference/android/content/Context#BIND_INCLUDE_CAPABILITIES) and [Messenger sending UID](https://developer.android.com/reference/android/os/Message#sendingUid). Background camera eligibility is verified on the physical phone separately from local build/tests and signed package verification.
