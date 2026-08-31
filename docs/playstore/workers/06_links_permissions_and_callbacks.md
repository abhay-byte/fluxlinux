# Worker 06 — Links, Install Permissions & Callback Hardening

## Goal
Preserve the proven v1.8 Play fixes and prevent external-link/deep-link regressions.

## Do
1. Confirm `REQUEST_INSTALL_PACKAGES` is absent.
2. Confirm no `ApkDownloader`, `PackageInstaller`, `ACTION_INSTALL_PACKAGE`, unknown-sources settings flow, or APK MIME installer remains in Play.
3. Audit every user-facing external URL. Remove direct `.apk`, `.apks`, `.xapk`, `.zip`, binary attachment, or auto-download install CTA.
4. External prerequisite buttons may open approved app/product/project landing pages only; FluxLinux must not download/install third-party APKs.
5. Audit `fluxlinux://callback`: accept callbacks only for active app-created task IDs and expected fixed fields.
6. Reject arbitrary shell commands, script paths, file paths, URLs, unexpected hosts/actions, duplicate/stale callbacks.
7. Add regression tests for spoofed callback inputs.

## Tests
```bash
rg -n 'REQUEST_INSTALL_PACKAGES|canRequestPackageInstalls|ACTION_INSTALL_PACKAGE|PackageInstaller|application/vnd.android.package-archive' app/src
rg -n 'https?://[^" ]+\.(apk|apks|xapk|zip)([?" ]|$)' app/src/zenithblue app/src/main fastlane || true
./gradlew :app:testZenithblueDebugUnitTest
```
Run adb deep-link negative tests with malformed, stale, unknown-task and injected values.

## Acceptance
- No APK-install permission or implementation in Play.
- No direct binary-install link exposed by Play UI/metadata.
- Valid callback flow still works.
- Spoofed/stale callbacks cannot trigger shell execution or state transitions.
