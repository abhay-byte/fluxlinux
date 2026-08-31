# Play Store Documentation

These documents define the Google Play build and release process for FluxLinux.

## Canonical v2 plan

Use these files for implementation:

| File | Purpose |
|---|---|
| `v2_0_compliance_roadmap.md` | **Canonical roadmap.** Keeps the embedded v2 PRoot backend and moves Flux-managed runtime delivery to Google Play Feature Delivery dynamic-feature modules. |
| `full_v2_compliant_delivery_execution_roadmap.md` | Technical supplement for the same PFD + native PRoot architecture. It is not a separate implementation track. |
| `WORKER_PROMPT.md` | Reusable execution contract to give an agent together with exactly one canonical worker file. |
| `workers/` | **Canonical worker sequence.** Execute Workers 01 through 10 in order. |

## Deprecated worker set

`compliant-runtime-workers/` is an older QEMU/interpreter-first experiment plan. It was superseded after code and policy review and must not be executed. The files in that directory redirect to the canonical worker sequence.

## Current architecture

The Play build remains an embedded Linux environment. Same-architecture native PRoot, terminal, X11, PulseAudio, distro extraction/configuration and session management are preserved. Small directly executed Android-host launchers remain in normal Play-installed native-library locations. The large host bootstrap and each distro rootfs are delivered on demand as **Play Feature Delivery dynamic-feature modules**.

The Play flavor must not contain a Flux-managed GitHub/HTTP rootfs or bootstrap fallback, Android-level root/chroot, nested loader APK, or obsolete external-Termux bridge.

### Do not use PAD for rootfs/bootstrap

Play Asset Delivery is not the canonical mechanism for Linux rootfs/bootstrap payloads. These archives contain executable-bearing runtime content; use Play Feature Delivery dynamic features.

### QEMU is not the primary backend

QEMU/interpreter execution is no longer the default same-architecture plan because it adds substantial performance overhead and is unnecessary for the existing native PRoot backend. Reconsider it only if a future explicit policy or platform requirement makes native PRoot unsuitable.

## Worker order

1. `workers/01_branch_baseline.md`
2. `workers/02_play_flavor_boundary.md`
3. `workers/03_remove_remote_executable_delivery.md`
4. `workers/04_remove_nested_and_writable_executables.md`
5. `workers/05_remove_root_chroot_from_play.md`
6. `workers/06_links_permissions_and_callbacks.md`
7. `workers/07_foreground_services.md`
8. `workers/08_privacy_and_store_metadata.md`
9. `workers/09_ci_policy_gate.md`
10. `workers/10_release_validation.md`

Do not start the next worker until the current worker reports `PASS`, except for a narrowly documented compile/test fix required to unblock it.

## Core invariants

- Play package remains `com.zenithblue.fluxlinux`.
- Current Play target-SDK and policy requirements are rechecked before release.
- The Play app does not fetch Flux-managed executable/rootfs/bootstrap payloads from GitHub/HTTP.
- PRoot fake guest root is preserved; Android/device root/chroot is absent from Play.
- Directly executed Android-host code follows the existing API-36/native-library W^X-safe design.
- Executable-bearing rootfs/bootstrap payloads use Play Feature Delivery, not PAD.
- PRoot is not described as a VM/interpreter.
- Another Play app is precedent, not a guarantee of approval.
- User-initiated guest package-manager behavior is an explicit Internal/Closed Play review gate.
- Live Google Play policy overrides stale repository assumptions.

## Reference documents

- `policies_and_violations.md`: historical and repo-specific findings. The canonical v2 roadmap overrides stale architecture assumptions in it.
- `privacy_policy.md`: current v1.8-era published Play privacy text. Worker 08 must rewrite/reconcile it with the exact final v2 AAB before release.
- Fastlane Play metadata lives under `fastlane/metadata/android/en-US/`.
