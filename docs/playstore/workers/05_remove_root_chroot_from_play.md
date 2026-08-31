# Worker 05 — Remove Root / Chroot from Play

## Goal
Compile root-only and chroot functionality out of the Google Play variant and remove root-related manifest surface.

## Do
1. Exclude chroot cards/settings/storage/process UI from `zenithblue`.
2. Exclude `RootShell`, BusyBox-path/root helpers, Magisk/KernelSU/APatch integration, and chroot scripts.
3. Remove `android.permission.ACCESS_SUPERUSER` from the Play merged manifest.
4. Ensure Play onboarding never asks for root.
5. Ensure no Play action runs `su`, root shell, mount, chroot, or root BusyBox command.
6. Keep root/chroot code available only in non-Play source sets.
7. Add contract tests showing Play distro catalog contains no chroot entries.

## Tests
```bash
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:processZenithblueReleaseMainManifest
./gradlew :app:assembleZenithblueDebug
# inspect merged manifest and built dex/resources
grep -R 'ACCESS_SUPERUSER' app/build/intermediates/merged_manifests/zenithblueRelease && exit 1 || true
```
Also scan the Play artifact for root/chroot command strings and manually verify UI.

## Acceptance
- No root permission in Play manifest.
- No root prompt/UI path.
- No chroot cards or chroot storage tools.
- No executable root/chroot implementation in Play artifact.
- Non-Play flavor still retains intended root features.
