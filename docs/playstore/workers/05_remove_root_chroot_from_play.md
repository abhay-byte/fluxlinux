# Worker 05 — Remove Android Root/Chroot, Preserve PRoot Fake Root

## Goal
Keep the normal PRoot Linux administration experience in Google Play while removing every Android-level root/chroot capability from `zenithblue`.

## Boundary to preserve

```text
PRoot guest uid 0 / `-0`
        !=
Android uid 0 / device root
```

PRoot fake root is required for ordinary distro administration. It must not be removed merely because Android root is removed.

## Tasks

1. Inventory all root/chroot paths, including `RootShell`, root availability probes, BusyBox/root resolver code, setup scripts, UI, settings, onboarding, manifest entries and build dependencies.
2. Compile out/remove from `zenithblue`:
   - `ACCESS_SUPERUSER`;
   - Magisk integration;
   - KernelSU integration;
   - APatch integration;
   - Android root shell/process managers;
   - real `chroot` launch/setup paths;
   - root-only mount/storage helpers;
   - root-only settings/cards/onboarding/prompts.
3. Ensure the Play distro registry/method selection cannot choose `chroot` even on a rooted device.
4. Keep the PRoot launch path, PRoot `-0` behavior, normal package-manager administration, and guest root semantics unchanged where possible.
5. Keep non-Play root/chroot implementation behind the `ivarna` source/dependency boundary if desired.
6. Remove root/chroot strings/resources from the Play artifact when they are no longer applicable.
7. Add tests proving a rooted Android device still selects the PRoot path in `zenithblue`.
8. Add a host/guest identity test:
   - inside guest, `id -u`/`whoami` can report root;
   - the Android process still runs as the application's normal sandbox UID and never requests superuser.

## Tests

```bash
./gradlew test
./gradlew assembleZenithblueDebug
./gradlew assembleIvarnaDebug
./gradlew bundleZenithblueRelease
```

Artifact/manifest checks should reject functional Play occurrences of:

```text
ACCESS_SUPERUSER
Magisk
KernelSU
APatch
real chroot/root grant UI
```

Device tests:

- unrooted device: install/start PRoot distro, guest fake root, terminal and desktop;
- rooted device: same flow, with no Android root prompt/use;
- package-manager smoke test inside guest;
- non-Play flavor still builds.

## Acceptance

- Play has no Android-root permission, prompt, manager or real-chroot path;
- Play always chooses PRoot;
- guest fake root remains functional;
- Android app UID remains unprivileged;
- `ivarna` remains buildable with its intended non-Play functionality.

## Do not

Do not remove PRoot `-0`, guest uid 0, normal Linux package-manager administration, or the embedded backend.