# Worker 07 — Foreground Service Minimization

## Goal
Retain only foreground services genuinely required by the Play feature set and align runtime behavior, manifest declarations, and Play Console declarations.

## Do
1. Review `InstallServerService`, `BaseInstallService`, `AppTerminalService`, and `DesktopSessionService` against the reduced Play feature set.
2. Remove services and type-specific permissions that are no longer required.
3. Avoid `specialUse` unless the Play build has a defensible, user-visible core use case requiring it.
4. For each retained FGS: user initiation, accurate notification, stop action, automatic stop when work ends, correct API 34–36 type.
5. Update notification text so it describes the actual active work.
6. Document exact Play Console declaration wording and demo steps.
7. Test notification permission denied and process/background transitions.

## Tests
```bash
./gradlew :app:lintZenithblueRelease
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:assembleZenithblueDebug
```
Device tests on API 34, 35 and 36 must show no `MissingForegroundServiceTypeException`, `ForegroundServiceStartNotAllowedException`, or stale notifications.

## Acceptance
- Only necessary FGS permissions/services remain.
- Runtime `startForeground()` type matches manifest.
- User can stop active work.
- Services stop promptly when work finishes/cancels.
- Play Console declaration text/demo checklist is committed.
