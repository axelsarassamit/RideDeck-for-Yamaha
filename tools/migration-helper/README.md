# RideDeck migration helper

Test-only, separately built Android instrumentation for a reviewed Yamaha or Dash APK. It restores a pinned schema-1 archive into a fresh, quiescent target without launching its real Application. Preparation or rehearsal does not authorize production removal or restoration. Keep the original raw backup and its integrity manifest.

## Build and identity

Build the target APK first. Compute its SHA-256, then build this project with that exact package and hash. Use the existing RideDeck Gradle wrapper; do not introduce a target-project dependency.

```powershell
$helper = Join-Path (Get-Location) 'tools/migration-helper'
$targetPackage = 'com.axelsarassamit.gx12.yamaha.preview'
$targetApk = Read-Host 'Absolute path to reviewed target APK'
$targetHash = (Get-FileHash -LiteralPath $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
& ./gradlew.bat -p $helper "-PtargetPackage=$targetPackage" "-PexpectedTargetApkSha256=$targetHash" testDebugUnitTest assembleDebug --no-daemon --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Helper build or tests failed' }
```

| Target package | Helper application ID suffix |
| --- | --- |
| `com.axelsarassamit.gx12.yamaha` | `yamaha_release` |
| `com.axelsarassamit.gx12.yamaha.preview` | `yamaha_preview` |
| `com.axelsarassamit.ridedeck.dash` | `dash_release` |
| `com.axelsarassamit.ridedeck.dash.debug` | `dash_debug` |

The helper application ID is `com.axelsarassamit.migrationhelper.` plus the suffix. The runner is `com.axelsarassamit.migrationhelper.MigrationInstrumentation`. APK outputs are under `build/outputs/apk/`. `assembleRelease` produces an unsigned release helper; this project defines no signing environment variables. The coordinating delivery process must sign it with the target's matching certificate. Debug helpers use the local debug certificate, which must also match the installed rehearsal target. Never use a production package merely to avoid a preview signing mismatch.

The helper has `android:testOnly=true`, no launcher and no network permission. Install it with `adb install -t`; installation and all device operations belong to the coordinator. Inspect the helper APK's package, instrumentation target, certificate, SHA and application metadata before use. Metadata key `com.axelsarassamit.migrationhelper.EXPECTED_TARGET_APK_SHA256` must equal the compiled pin and installed target APK SHA. A zero pin or split target APK is rejected.

Before target providers initialize, the gate checks the exact APK and allows only the reviewed FileProvider, DashBridgeProvider for Dash, and a nonexported AndroidX Startup provider whose single metadata item is `androidx.profileinstaller.ProfileInstallerInitializer=androidx.startup`. New providers or initializers require fresh review. The real target Application is replaced with a plain Application and its `onCreate` is suppressed. The profile installer may produce `files/profileInstalled`; that marker is ignored, never archived, overwritten or removed. This is reviewed-build startup suppression, not a general Android sandbox.

## Archive operations

Keep all companion host apps stopped throughout migration so a trusted host cannot request recording or appearance changes during restore. Do not open the target or riding apps until verification finishes.

The coordinator must explicitly select the device, prepare a fresh isolated target and approve its USB installation. No command below uninstalls or clears a target. Do not run against a riding, recording or otherwise active target. These commands assume the target and matching helper are already installed; set the component for the selected variant.

```powershell
$adb = 'C:/Users/axels/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$serial = Read-Host 'Explicit rehearsal device serial'
$archive = Read-Host 'Absolute path to approved archive from manifest-v3.json'
$archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
# Compare this hash with the authoritative private preparation manifest before proceeding.
$component = 'com.axelsarassamit.migrationhelper.yamaha_preview/com.axelsarassamit.migrationhelper.MigrationInstrumentation'
& $adb -s $serial push $archive /data/local/tmp/ridedeck-migration.zip
& $adb -s $serial shell chmod 600 /data/local/tmp/ridedeck-migration.zip
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e archiveSha256 $archiveHash -e operation validate $component
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e archiveSha256 $archiveHash -e operation restore -e acknowledgeFreshTarget YES $component
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e archiveSha256 $archiveHash -e operation verify $component
```

Inspect the instrumentation result, not merely the host command exit code: require `VALIDATED`, `RESTORED`, then `VERIFIED`. Failures return fixed codes and sanitized exception type/errno, never preference values. `validate` stages/checks the archive and fresh-target inventory; it does not restore user files. `verify` checks the archive-owned subset and can run after the separate provider import. Count fields report files, preference maps and values.

Schema 1 has exactly `schema`, `sourcePackage`, `targetPackage`, `snapshotSha256`, and `files`; each file has exactly `path`, `bytes`, `sha256`. The target must match the helper; source is the canonical production Yamaha or Dash package. The runtime archive SHA pins the complete ZIP. Bounds: 20,000 entries, 4 GiB per file, 8 GiB combined data, 4 MiB manifest/preferences. Duplicate JSON keys/ZIP entries, unsafe paths, symlinks, unexpected members, sizes and hash mismatches are rejected before user-data writes. Preferences are parsed and restored through typed Editor operations, preserving supported value types. Encrypted preference XML is never restored.

Yamaha permits only reviewed preference names, `files/bike-diagnostics.txt`, `files/mbgl-offline.db`, and `databases/yamaha-rides.db`. The staged ride DB must have a SQLite header and pass read-only `PRAGMA quick_check`. No journal/WAL/SHM is accepted in archive or target. The host packer separately checks and omits only the reviewed inactive zero-header source journal, retaining it in the raw backup. Unknown source user state blocks packing. Dash permits reviewed preferences and `files/recordings/` content. These are explicit migration inventories, not a claim to preserve every possible Android state.

## No replacement and recovery

Matching existing files/maps are preserved. Conflicts and unexpected target user files are rejected. Each new file uses `O_CREAT|O_EXCL|O_WRONLY|O_NOFOLLOW` with mode 0600; no rename-overwrite or hard-link fallback is used. The new file's device/inode identity is durably journaled before its first byte, then the copy is synced and its complete size/hash verified.

The helper keeps archive, stage and journal under target `no_backup/ridedeck-migration-v1` while an operation is pending. A shared lock serializes archive/provider operations. Do not clear, replace or manually delete this workspace to bypass a refusal.

Completed archive cleanup retires `journal.json` first, only after a verified restore, verified completed operation, or fully completed rollback, before deleting retained archive/stage files. A kill during subsequent cleanup leaves disposable staging that the next pinned-input verification can replace, without a journal referring to a deleted archive. Pending journals are never retired before verification or rollback succeeds. Preview-only `simulateCleanupPause=YES` pauses for 15 seconds once after completed-journal retirement so the coordinator can rehearse this interruption; production targets and unsupported operations reject it before staging writes.

Rollback validates all affected state before deleting anything. A new file must match its recorded device/inode, bounded size and exact verified source prefix; complete files must also match the full source. Preference cache and disk must agree and remain empty or equal the imported values. Later user changes or an unknown inode cause refusal. A crash after exclusive creation but before durable ownership intentionally retains the file and journal; it cannot safely be auto-deleted. This is a known manual-review case, not successful rollback.

For a pending archive journal, explicitly request recovery with the same pinned archive:

```powershell
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e archiveSha256 $archiveHash -e operation recover -e acknowledgeRecovery YES $component
```

Require `RECOVERED`. A completed journal can be verified/cleaned, not rolled back. A conflict must be investigated without deleting user state. Files, preferences and Android Keystore do not share a single atomic transaction; sudden device/storage failure may require manual recovery. Rehearsal is required before a separately authorized production migration.

## Separate Yamaha provider import

Use the private configuration file through the coordinator; never print its contents or paste values into command arguments. It must be UTF-8 JSON with exactly `maptiler` and `tomtom`, bounded nonempty supported strings, and at most 4096 bytes. The fixed device path is `/data/local/tmp/ridedeck-provider-overrides.json`. Its SHA is passed separately. Archive restoration must finish before import.

```powershell
$providerFile = Read-Host 'Absolute path to private provider configuration'
$providerHash = (Get-FileHash -LiteralPath $providerFile -Algorithm SHA256).Hash.ToLowerInvariant()
& $adb -s $serial push $providerFile /data/local/tmp/ridedeck-provider-overrides.json
& $adb -s $serial shell chmod 600 /data/local/tmp/ridedeck-provider-overrides.json
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e providerSha256 $providerHash -e operation importProviderOverrides -e acknowledgeFreshTarget YES $component
& $adb -s $serial shell am instrument -w -e targetPackage $targetPackage -e providerSha256 $providerHash -e operation verifyProviderOverrides $component
```

Require `IMPORTED`, then `VERIFIED` with two providers. The helper calls target `NavigationSecrets.save`, waits for persistent preferences, independently decrypts saved AES-GCM ciphertext and checks the target read result. Existing matching overrides are skipped; conflicting or unknown entries are refused. Journals contain ownership names and pin, never plaintext keys. Provider failure retains a separate journal for explicit `recoverProviderOverrides` with the same provider file/hash and `acknowledgeRecovery=YES`. Recovery prevalidates its entire journal and entries before removal, preserves prior overrides/alias, and removes a newly created alias only when no provider entries remain. It is not automatic rollback. Remove the fixed temporary ZIP/provider inputs through the coordinator after successful verification or after retaining them for an investigation.

## Rehearsal evidence and limits

As reported by the coordinating chat on 2026-11-12: combined helper JVM tests/build passed; Yamaha emulator validated, restored and verified three files/three preference maps. A 4096-byte partial-copy fault rolled back; an after-create fault safely refused rollback and explicit recovery. Separate instrumentation processes imported and verified both real provider entries without logging values. Dash emulator also validated, restored and verified ten files/two preference maps, including both recording files; its partial-copy fault rolled back. Yamaha archive verification after provider import passed. The private raw snapshots remain intact; only reviewed restore inventories were exercised. An actual emulator process stop during a durable4096-byte partial copy was followed by successful explicit recovery, restore and verification. An edited existing file was rejected and preserved byte-for-byte. A separate process stop after completed-journal retirement also recovered through fresh pinned verification; a normal repeat restore then passed. These results do not establish production phone migration, road behavior, camera capture or compatibility with a different target APK.

Preview-only fault arguments are `simulateFailureAfter=N`, `simulateCopyFailureAfterBytes=N`, `simulateCopyPauseAfterBytes=N` (preview restore only; fsync then pause 15 seconds once for an external process-stop rehearsal), and `simulateFailureAfterCreate=YES`. Use fresh disposable isolated targets, never production. An after-create refusal may require coordinator-controlled reset of that disposable target; it does not authorize clearing a production app. The pure regression suite has 19 test methods covering settings and ownership/prefix decisions; it complements, rather than replaces, real filesystem/process-death recovery rehearsals.
