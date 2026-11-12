# November development review

Yamaha 0.12.64/code1264 retains its separate map screen and Yamaha-only setup
category. General menus, native light/dark colours, dialog surfaces and 64dp
setup actions agree with the RideDeck family. The riding quick-action rail now
has enough actual height for its buttons; Rain Lock's full label is visible.

Navigation no longer displays a fabricated fuel range. Permission handling and
ride-time calculations were repaired; a rejected Dash renewal no longer reports
success. Debug companions use isolated package IDs and matching debug signatures;
production retains the original companion certificate checks. Lab launches the
actual separate workshop app rather than introducing workshop controls here.

Provider defaults now come from private build configuration or encrypted user
overrides. Literal provider keys were removed from current source. Prior committed
values remain in history and require provider-account restriction/rotation review
before a public release. No Git history rewrite or external key rotation was done.

Local app validation: 253 JVM tests, app/instrumentation assembly, zero-error lint.
Ten focused tests passed on the authorized phone preview, including measured
button bounds, map menus, Rain Lock and app closure. This does not prove bike
hardware, road guidance or incoming-call audio.

The separate tools/migration-helper project does not enter the riding APK. It
supports a reviewed move from accidentally debug-signed production installations
back to original release keys. Private emulator rehearsals checked settings,
ride database, provider encryption, partial-copy rollback, process-death recovery
and refusal to overwrite edited files. Source CI, original-key signed delivery,
fresh backups and separate owner approval remain mandatory before any production
uninstall. See the helper README for strict inventory and recovery limits.
