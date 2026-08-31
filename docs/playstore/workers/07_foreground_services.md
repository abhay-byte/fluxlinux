# Worker 07 — Foreground Services for PAD + Embedded Linux Sessions

## Goal
Keep only foreground services that are genuinely required by the embedded terminal/desktop experience, and stop using custom download services for distro delivery.

## Tasks
1. Review each service independently:
   - `InstallServerService`;
   - `BaseInstallService`;
   - `AppTerminalService`;
   - `DesktopSessionService`.
2. PAD download/progress must come from Google Play delivery APIs; remove or narrow custom HTTP/data-sync download service behavior that existed only for rootfs downloads.
3. For each remaining service document:
   - why it is core user-visible functionality;
   - exact start trigger;
   - exact stop condition;
   - notification text/action;
   - Android FGS type and permission;
   - Play Console declaration wording.
4. Remove unused `FOREGROUND_SERVICE_*` permissions/types.
5. Avoid `specialUse` if a more specific supported type/no FGS is valid. If `specialUse` is truly required for a live Linux/desktop session, document and test the justification precisely.
6. Ensure terminal/desktop services are user-started or otherwise compliant with Android background-start restrictions.
7. Add a visible stop action that terminates the associated session cleanly.
8. Ensure app/process death and session exit stop stale notifications/services.
9. Test notification denial and Android 14–16 behavior.

## Tests
```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew assembleZenithblueDebug
```

Device matrix API 34/35/36:
- start distro download via PAD;
- background/foreground during download;
- start terminal/desktop;
- lock screen/background app;
- stop from notification;
- kill app/process;
- verify no `MissingForegroundServiceTypeException` or `ForegroundServiceStartNotAllowedException`.

## Acceptance
- no custom FGS exists solely to perform GitHub rootfs downloads;
- every retained FGS has an accurate type, permission, notification, and stop condition;
- terminal/desktop sessions remain reliable;
- Play Console declaration text is recorded for worker 08/10.

## Do not
Do not remove a genuinely necessary session service only to reduce manifest size; make it compliant and accurately declared.