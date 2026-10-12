# Yamaha 0.12.34

Three damaged strings in the bike connection service are corrected: the ongoing
notification, connecting message and live casting status. Bullets, the ellipsis
and display dimensions now use explicit Unicode escapes in source.

Every debug and signed release build checks UTF-8 source, resources, documentation
and build scripts before compiling. The check rejects malformed UTF-8 and common
double-decoding damage, including Unicode-escaped damage. Five regression tests
cover detection, proper Thai and other languages, symbols, emoji, and DEX decoding.
Java compilation explicitly uses UTF-8.

The Android workflow also scans readable strings in the compiled APK. Binary Kotlin
metadata is excluded because it is not displayed text. Reports contain file paths
and line numbers or DEX string indexes, without printing app text or credentials.

This guards app-owned text. It cannot guarantee the content or font rendering of
messages and map data supplied by other apps and providers. The bike-map layout
and English guidance from 0.12.33 are retained.
