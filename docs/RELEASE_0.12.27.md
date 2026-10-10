# RideDeck for Yamaha 0.12.27

Dash controls appear only when the launchable `com.axelsarassamit.ridedeck.dash` package has the verified permanent Dash release signer. Yamaha checks availability when the cockpit is built and whenever it resumes. If Dash is absent, disabled or signed with a different key, the dock shows Ride and the regular Start, Pause, Resume and End timer controls remain available. The ride page hides Dash's launch button, status and recording explanation. An already-open ride page also refreshes that availability.

The launch action rechecks the installed identity before opening Dash, and bridge calls use the same check. Android 9 and newer use the platform's [signing-certificate verification](https://developer.android.com/reference/android/content/pm/PackageManager#hasSigningCertificate(java.lang.String,%20byte[],%20int)), including verified signing history. Android 8 uses the package's single certificate SHA-256. The existing package visibility declaration is retained.

The Dash provider protocol, explicit ride lifecycle, large controls, Yamaha icon and cockpit arrangement are preserved. Validation records the full local tests, both current Android workflows, the matching signed workflow and coordinated phone checks separately.
