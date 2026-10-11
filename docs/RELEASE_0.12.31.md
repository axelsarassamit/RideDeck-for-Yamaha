# RideDeck for Yamaha 0.12.31

The Xiaomi Android 16 test of 0.12.30 confirmed that the one-use recording ticket started Dash's foreground service, but camera and microphone access remained denied. The visible host now explicitly supplies its sender privileges through ActivityOptions when sending the signed immutable foreground-service PendingIntent. Android 16 uses ALLOW_IF_VISIBLE; Android 14 and 15 use ALLOWED only after the existing visible-current-gesture checks. Earlier versions retain the existing send path.

The ticket still comes from the verified Dash package and UID, is consumed only once during the same visible ride gesture, expires after ten seconds on the host, and is sent with no fill-in Intent. No Dash activity is opened. REC still requires fresh confirmation of actual capture.

Recording messages in the ride page, preview and status reply are now short and readable. Technical exception text is replaced with a concise action message; detailed Android failures remain in Dash's diagnostic logs.

References: [ActivityOptions sender modes](https://developer.android.com/reference/android/app/ActivityOptions#setPendingIntentBackgroundActivityStartMode(int)) and [Android 16 PendingIntent service handling](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/services/core/java/com/android/server/am/PendingIntentRecord.java). Physical verification with matching Dash 0.5.4 remains required.
