# RideDeck for Yamaha 0.12.30

The Xiaomi Android 16 test of 0.12.29 found that starting Dash's internal recording service from its bound service was blocked. Yamaha now sends Dash's one-use foreground-service PendingIntent from the still-visible explicit Start/Resume gesture, keeping Yamaha on screen.

The authenticated what=1 reply must contain a startIntent token from the verified signed Dash package and UID. On Android 12 or newer the token must be immutable and target a foreground service. It is sent once without any fill-in Intent; a new request is required after cancellation or failure. The host's ten-second request deadline, current ride generation, visible UI and camera/microphone grants are checked immediately before sending. Dash separately enforces a fifteen-second token expiry and authenticated lease ownership at redemption.

Pending token delivery is cancelled on leaving or closing the host screen, Pause, End or service teardown. No token is stored in preferences or sent by heartbeats, background restarts or reconnections. Older replies without the token fail with a visible retry/update message.

The native rear/front angle preview, large Done control, and fresh camera-confirmed red REC indicator are preserved. Token delivery means recording requested; REC still requires actual capture confirmation and expires after three seconds without a valid status.

Reference: [Android's visible-app PendingIntent exemption](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions) and [PendingIntent creator/type checks](https://developer.android.com/reference/android/app/PendingIntent). Builds and signed APK verification do not substitute for the coordinated physical phone test.
