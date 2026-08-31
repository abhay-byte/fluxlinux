# Worker 07 — Foreground Services After Play Feature Delivery

## Goal
Make foreground-service behavior match the corrected architecture: Google Play owns dynamic-feature downloads; Flux services are used only for justified user-visible local install/session work.

## Services to audit

- `InstallServerService`;
- `BaseInstallService`;
- `AppTerminalService`;
- `DesktopSessionService`.

## Required lifecycle correction

Do **not** start/hold `BaseInstallService` merely while Play downloads `:runtime_host` or a distro feature.

Correct order:

```text
user requests feature
 -> Play Feature Delivery downloads/installs module
 -> required modules installed
 -> Flux materializes/verifies/extracts/configures payload
 -> BaseInstallService only if that local work genuinely requires FGS
 -> stop service
```

## Tasks

1. Remove `InstallServerService` from the Play flavor if Worker 06 confirms it is only the deprecated external-Termux bridge.
2. Remove custom HTTP/data-sync FGS behavior previously used for rootfs/bootstrap download.
3. Refactor `OnboardingInstallRunner`/install lifecycle so feature-module request/progress occurs before `BaseInstallService.start(...)`.
4. Start `BaseInstallService` only for local materialization/verification/extraction/configuration when needed to survive backgrounding.
5. Stop `BaseInstallService` on success, failure, cancellation, stale generation, process teardown and unrecoverable feature/materialization failure.
6. For every remaining FGS, document:
   - core user-visible purpose;
   - exact user action/start trigger;
   - exact stop condition;
   - notification text/actions;
   - visible stop action where applicable;
   - Android FGS type + permission;
   - why no narrower lifecycle/type is sufficient;
   - Play Console declaration wording.
7. Audit `AppTerminalService` and `DesktopSessionService` independently.
8. Prefer a specific supported FGS type when accurate. If `specialUse` is truly required for long-running interactive Linux/desktop sessions, record the precise justification and declaration text.
9. Ensure services are user-started or otherwise comply with Android background-start restrictions.
10. Ensure session exit/process death removes stale notifications/services.
11. Test notification denial, app backgrounding, screen lock, task removal and process death on Android 14–16.

## Tests

```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew assembleZenithblueDebug
./gradlew bundleZenithblueRelease
```

Device matrix API 34/35/36:

- request dynamic feature with app foreground/backgrounded;
- confirm no Flux download FGS is required for Play's feature transfer;
- module installed -> local extraction/config begins;
- cancel local install;
- terminal start/stop;
- desktop start/stop;
- lock screen/background;
- stop from notification/action;
- kill process/app;
- verify no stale notification;
- verify no `MissingForegroundServiceTypeException` or `ForegroundServiceStartNotAllowedException`.

## Acceptance

- PFD network transfer is not implemented as a Flux HTTP/dataSync FGS;
- deprecated `InstallServerService` is absent from Play when no longer needed;
- `BaseInstallService` starts only after required feature modules are available and only for justified local work;
- every retained FGS has an accurate type, permission, visible notification and stop condition;
- terminal/desktop sessions remain reliable;
- declaration wording is recorded for Workers 08/10.

## Do not

Do not keep a service simply because v2 already had it. Do not remove a genuinely necessary interactive-session FGS solely to reduce manifest size; make it compliant and precisely declared.