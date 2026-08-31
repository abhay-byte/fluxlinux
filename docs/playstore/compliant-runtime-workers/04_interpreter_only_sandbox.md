# Worker 04 — Interpreter-Only Sandbox and Fail-Closed Guard

## Goal

Make it structurally impossible for the Play build to execute guest Linux code directly on Android.

## Implement

Create a central policy/execution guard around all Play guest launches.

Required invariants:

- guest ELF -> QEMU/interpreter only
- guest `.so` -> never Android `System.load`/`dlopen`
- guest script interpreter -> guest/interpreted shell only
- missing/crashed QEMU -> stop
- no `/system/bin/linker64 <guest ELF>` fallback
- no Android chroot/root path

## Filesystem boundary

For QEMU-user + PRoot:

- guest `/` maps only to distro tree
- no bind of Android `/system`, `/vendor`, app native-lib directory or broad `/dev`
- expose only explicit app-private/shared paths
- prevent `..`/symlink escape to host

Add a `GuestPolicyGuard` or equivalent to validate requests before starting them.

## Negative tests

Attempt all of these and require failure/isolation:

1. direct host path to guest ELF
2. guest points to `/system/bin/sh`
3. guest points to app `nativeLibraryDir`
4. guest ELF renamed `.so`
5. symlink escape from guest root
6. missing QEMU engine
7. QEMU crash
8. malformed argv/path

Instrument process-launch wrappers in tests where possible to prove no fallback process is spawned.

## Acceptance

- [ ] Central Play execution guard exists.
- [ ] Every Play guest launch flows through it.
- [ ] No guest direct-exec fallback.
- [ ] Host system/native-lib paths are inaccessible to guest launch resolution.
- [ ] Negative escape tests pass.
- [ ] `ivarna` remains unaffected.
