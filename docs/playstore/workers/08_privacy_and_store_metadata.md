# Worker 08 — Privacy Policy, Data Safety, Play Metadata, and Reviewer Notes

## Goal
Make every Play-facing statement match the exact final `zenithblue` AAB: embedded PRoot, Google Play Feature Delivery modules, no Android root/chroot, and the actual network/FGS/dependency behavior.

## Tasks

1. Re-audit the final Play dependency graph, merged manifest, network paths, dynamic-feature modules and services before writing declarations.
2. Rewrite/update the Play privacy policy for `com.zenithblue.fluxlinux` to state accurately that:
   - FluxLinux contains an embedded Linux userspace environment;
   - large Flux-managed host runtime and selected distro payloads are delivered as Google Play Feature Delivery modules and materialized/extracted into app-private storage;
   - Play uses PRoot fake root, not Android/device root or real chroot;
   - PRoot shares the Android kernel and guest processes remain under the FluxLinux Android application UID;
   - user-run Linux programs and package managers can make network connections to sites/repositories chosen by those programs/users;
   - logs/diagnostics, retention/deletion and any exported diagnostic behavior are described exactly;
   - analytics, ads, crash reporting, accounts, telemetry and identifiers are described exactly as implemented.
3. Remove stale claims about:
   - external Termux/Termux:X11 prerequisites;
   - GitHub rootfs/bootstrap runtime downloads;
   - Android root/chroot in the Play flavor;
   - APK/unknown-source installation.
4. Update Play/Fastlane title/short/full descriptions and release notes to advertise only functionality present in the exact AAB.
5. Update screenshots/help/onboarding text that show removed root/chroot/external-Termux/installer flows.
6. Rebuild Data Safety answers from actual code/dependencies/network behavior. Do not copy old v1.8/v2 answers blindly.
7. Reconcile foreground-service declarations with Worker 07's final service matrix.
8. Write reviewer notes explaining the architecture factually:
   - base app + on-demand PFD modules;
   - PRoot userspace isolation/fake root;
   - no Android-level root/chroot;
   - no Flux-managed rootfs/bootstrap executable download from GitHub/HTTP;
   - user-initiated guest package managers may access normal Linux repositories.
9. Do **not** call PRoot a VM or interpreter and do not claim another app guarantees compliance.
10. Confirm the public privacy-policy URL and in-app privacy link are reachable.
11. Produce a release declaration checklist for Worker 10: Data Safety, FGS declarations, content rating/target audience as applicable, privacy URL, store listing and reviewer notes.

## Tests

- search Play metadata/privacy/help for stale `GitHub rootfs`, external-Termux prerequisite, Android-root/chroot and installer wording;
- compare final merged manifest to privacy/FGS declarations;
- inspect `zenithblue` dependency tree for analytics/telemetry/crash SDKs;
- inspect final dynamic feature names/delivery behavior and ensure reviewer text uses PFD terminology;
- fresh install the Play build and verify every advertised major feature exists;
- verify privacy URL from a clean browser/session.

## Acceptance

- privacy policy matches the final runtime/network/permissions/dependencies;
- Data Safety has been re-audited;
- PFD is described accurately; PAD is not claimed as the rootfs mechanism;
- PRoot is described factually, not as a VM/interpreter;
- store listing no longer claims GitHub runtime/rootfs delivery;
- no Play-facing text advertises removed Android-root/chroot or external APK installation;
- reviewer notes disclose the user-initiated guest package-manager behavior instead of hiding it;
- FGS declarations match Worker 07 and the final manifest.

## Do not

Do not write declarations from assumptions. The final AAB and runtime behavior are the source of truth.