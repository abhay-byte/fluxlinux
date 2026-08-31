# Worker 05 — Remove Android Root/Chroot, Preserve PRoot Fake Root

## Goal
Keep the normal PRoot Linux experience in Google Play, including guest `root` behavior, while removing Android-level root/chroot integrations.

## Tasks
1. Compile out of `zenithblue`:
   - `ACCESS_SUPERUSER`;
   - Magisk integration;
   - KernelSU integration;
   - APatch integration;
   - Android root shell/process managers;
   - real `chroot` launch path;
   - root-only mount/storage management;
   - root-only settings/cards/onboarding.
2. Keep PRoot distro launch unchanged where possible.
3. Verify guest fake UID 0 still works for normal distro administration (`apt`, `apk`, etc.) without requesting Android root.
4. Ensure Play UI never asks the user to grant root or install/enable a root manager.
5. Remove Play-only root/chroot strings, manifest entries, services, and dependency wiring.
6. Keep non-Play root/chroot implementation available behind `ivarna` source/dependency boundaries if desired.
7. Add regression tests that Play selects PRoot even on a rooted device.

## Tests
```bash
./gradlew test
./gradlew assembleZenithblueDebug
./gradlew bundleZenithblueRelease
```

Manifest/artifact checks:
```bash
grep -R "ACCESS_SUPERUSER\|Magisk\|KernelSU\|APatch" <play-output>
```
Expected: no functional Play root integration.

Device tests:
- unrooted device: install distro, `whoami`, package-manager operation, desktop start;
- rooted device: same flow, and FluxLinux must not request or use Android root;
- verify PRoot fake root remains functional.

## Acceptance
- Play app has no Android-root permission/prompt/path;
- real chroot is unavailable in Play;
- PRoot remains the normal backend;
- Linux guest fake root still works;
- non-Play flavor remains buildable.

## Do not
Do not remove Linux guest root semantics, PRoot `-0`, package-manager functionality, or normal rootfs administration just because Android root is being removed.