# Worker 08 — Privacy Policy, Data Safety, and Play Metadata

## Goal
Make every Play-facing statement match the final embedded PRoot + Play Asset Delivery implementation.

## Tasks
1. Rewrite/update the privacy policy for `com.zenithblue.fluxlinux`.
2. State accurately that:
   - FluxLinux contains an embedded Linux userspace runtime;
   - selected distro payloads are delivered through Google Play and extracted into app-private storage;
   - the Play build uses PRoot fake root, not Android root/chroot;
   - user-run Linux tools/package managers may make network connections;
   - local logs/diagnostics behavior and deletion/retention are described;
   - analytics, ads, crash reporting, accounts, telemetry, and identifiers are described exactly as implemented.
3. Remove stale claims about external Termux/Termux:X11 prerequisites and GitHub rootfs downloads.
4. Update Play/Fastlane description to advertise embedded terminal, distro-on-demand installs, desktop/X11, PulseAudio, and supported GPU features only if they actually ship in the final AAB.
5. Update screenshots/changelog/help text that show root/chroot or external APK installation.
6. Re-audit Data Safety from the final dependency graph/network behavior; do not copy old answers blindly.
7. Add/update reviewer notes explaining PAD distro delivery and PRoot sandbox at a high level without making unsupported policy claims.
8. Reconcile FGS declarations with worker 07.
9. Confirm public privacy-policy URL and in-app privacy link work.

## Tests
- search metadata/privacy for `GitHub rootfs`, external Termux prerequisites, Android root/chroot claims, obsolete permissions;
- compare final merged Play manifest with privacy/FGS text;
- inspect dependency tree for analytics/telemetry SDKs;
- fresh-install Play build and verify every advertised major feature exists.

## Acceptance
- privacy policy matches final runtime/network/permissions;
- Data Safety is re-reviewed rather than inherited from v1.8/v2 text;
- store description no longer says Flux downloads rootfs from GitHub;
- no Play screenshot/text advertises removed Android-root/chroot behavior;
- reviewer notes clearly identify Google Play Asset Delivery as distro source.

## Do not
Do not claim that PRoot is a VM or that Acode proves automatic policy approval. Describe the actual implementation factually.